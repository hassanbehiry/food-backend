package com.food.foodapp.cart.service;

import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.dto.CartAddItemRequest;
import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.dto.CartSyncItemRequest;
import com.food.foodapp.cart.dto.CartSyncRequest;
import com.food.foodapp.cart.dto.CartUpdateItemRequest;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.cart.mapper.CartMapper;
import com.food.foodapp.cart.repository.CartItemRepository;
import com.food.foodapp.cart.repository.CartRepository;
import com.food.foodapp.common.exception.CartItemNotFoundException;
import com.food.foodapp.common.exception.CartRestaurantConflictException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.MenuItemNotFoundException;
import com.food.foodapp.common.exception.MenuItemUnavailableException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.menu.repository.MenuItemRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side cart domain: get/create the caller's standing cart, whole-cart sync,
 * and the item-level operations sync is built from. The caller is always resolved
 * via {@link UserContext} — no method here accepts a customer id from a caller, so
 * nothing here can be tricked into acting on someone else's cart.
 * <p>
 * Every read reconciles the cart first ({@link #reconcile(Cart)}): items whose menu
 * item has gone unavailable, or whose restaurant is no longer customer-visible, are
 * dropped before anything is validated or returned, so a stale item can never
 * "survive" a read the way the task describes for sync specifically — this codebase
 * treats it as a standing invariant of the cart itself.
 * <p>
 * Each mutation keeps the in-memory {@code Cart.items} collection consistent with
 * what it just persisted and builds the response from that, rather than re-querying
 * afterward — re-running the fetch-join query against an entity already loaded in
 * the same persistence context is unnecessary and JPA session semantics around
 * refreshing an already-initialized collection mid-transaction are easy to get
 * subtly wrong.
 * <p>
 * Every mutating method loads the cart via {@link CartRepository#findByCustomerIdForUpdate},
 * which takes a pessimistic write lock on the cart row for the rest of the transaction. This
 * is what makes concurrent requests against the same cart (two rapid taps on "add", or two
 * overlapping sync calls) safe: the second request blocks until the first commits, instead of
 * both reading the same pre-mutation item set and racing to insert/update it.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final UserContext userContext;

    @Transactional
    public CartResponse getCart() {
        Cart cart = loadOrCreateCart();
        reconcile(cart);
        return CartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse syncCart(CartSyncRequest request) {
        Cart cart = loadOrCreateCartForUpdate();
        List<CartSyncItemRequest> requestedItems = request.getItems();

        if (requestedItems.isEmpty()) {
            replaceItems(cart, null, Map.of(), Map.of());
            return CartMapper.toResponse(cart);
        }

        // The sync payload is the desired end state, not a stream of increments: if the same
        // menuItemId appears more than once (e.g. the client merged a rapid double-tap locally
        // before flushing), the last occurrence wins rather than being rejected or summed.
        Map<Long, Integer> quantityByMenuItemId = new LinkedHashMap<>();
        for (CartSyncItemRequest item : requestedItems) {
            quantityByMenuItemId.put(item.getMenuItemId(), item.getQty());
        }

        List<MenuItem> menuItems = menuItemRepository.findAllByIdWithRestaurant(quantityByMenuItemId.keySet());
        Restaurant restaurant = validateItemsAndResolveRestaurant(quantityByMenuItemId.keySet(), menuItems);
        Map<Long, MenuItem> menuItemsById = menuItems.stream().collect(Collectors.toMap(MenuItem::getId, item -> item));

        replaceItems(cart, restaurant, quantityByMenuItemId, menuItemsById);
        return CartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(CartAddItemRequest request) {
        Cart cart = loadOrCreateCartForUpdate();
        reconcile(cart);

        MenuItem menuItem = requireAvailableMenuItem(request.getMenuItemId());
        Restaurant restaurant = menuItem.getRestaurant();
        if (cart.getRestaurant() != null && !cart.getRestaurant().getId().equals(restaurant.getId())) {
            throw new CartRestaurantConflictException(
                    "Cart already contains items from a different restaurant; clear the cart before adding items from another one");
        }

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(item -> item.getMenuItem().getId().equals(menuItem.getId()))
                .findFirst();
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int mergedQuantity = item.getQuantity() + request.getQuantity();
            if (mergedQuantity > CartItem.MAX_QUANTITY_PER_ITEM) {
                throw new InvalidRequestParameterException(
                        "quantity must be at most " + CartItem.MAX_QUANTITY_PER_ITEM + " per item");
            }
            item.setQuantity(mergedQuantity);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setMenuItem(menuItem);
            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);
            cart.getItems().add(item);
        }

        if (cart.getRestaurant() == null) {
            cart.setRestaurant(restaurant);
            cartRepository.save(cart);
        }

        return CartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long cartItemId, CartUpdateItemRequest request) {
        Cart cart = loadOrCreateCartForUpdate();
        reconcile(cart);

        CartItem item = requireItem(cart, cartItemId);
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        return CartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long cartItemId) {
        Cart cart = loadOrCreateCartForUpdate();
        reconcile(cart);

        CartItem item = requireItem(cart, cartItemId);
        cartItemRepository.delete(item);
        cart.getItems().remove(item);
        if (cart.getItems().isEmpty()) {
            cart.setRestaurant(null);
            cartRepository.save(cart);
        }

        return CartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse clearCart() {
        Cart cart = loadOrCreateCartForUpdate();
        replaceItems(cart, null, Map.of(), Map.of());
        return CartMapper.toResponse(cart);
    }

    private Restaurant validateItemsAndResolveRestaurant(Set<Long> requestedIds, List<MenuItem> found) {
        if (found.size() != requestedIds.size()) {
            Set<Long> foundIds = found.stream().map(MenuItem::getId).collect(Collectors.toSet());
            Long missingId = requestedIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new MenuItemNotFoundException("Menu item not found: " + missingId);
        }
        for (MenuItem menuItem : found) {
            if (!menuItem.isAvailable()) {
                throw new MenuItemUnavailableException("Menu item " + menuItem.getId() + " is not currently available");
            }
        }
        Set<Long> restaurantIds = found.stream().map(item -> item.getRestaurant().getId()).collect(Collectors.toSet());
        if (restaurantIds.size() > 1) {
            throw new InvalidRequestParameterException("All items in a sync request must belong to the same restaurant");
        }
        Restaurant restaurant = found.get(0).getRestaurant();
        if (!RestaurantService.isCustomerVisible(restaurant)) {
            throw new RestaurantNotFoundException("Restaurant not found: " + restaurant.getId());
        }
        return restaurant;
    }

    /**
     * Replaces the cart's entire item set in one shot — used by sync and clear.
     * {@code menuItemsById} must already hold fully-loaded entities (not lazy
     * references) for every key in {@code quantityByMenuItemId}, so the response
     * built from the resulting cart never triggers a lazy-load per item.
     */
    private void replaceItems(Cart cart, Restaurant restaurant, Map<Long, Integer> quantityByMenuItemId,
                               Map<Long, MenuItem> menuItemsById) {
        cartItemRepository.deleteByCartId(cart.getId());
        cartItemRepository.flush();
        cart.getItems().clear();
        cart.setRestaurant(restaurant);
        cartRepository.save(cart);

        for (Map.Entry<Long, Integer> entry : quantityByMenuItemId.entrySet()) {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setMenuItem(menuItemsById.get(entry.getKey()));
            item.setQuantity(entry.getValue());
            cartItemRepository.save(item);
            cart.getItems().add(item);
        }
    }

    private MenuItem requireAvailableMenuItem(Long menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException("Menu item not found: " + menuItemId));
        if (!menuItem.isAvailable()) {
            throw new MenuItemUnavailableException("Menu item " + menuItemId + " is not currently available");
        }
        if (!RestaurantService.isCustomerVisible(menuItem.getRestaurant())) {
            throw new RestaurantNotFoundException("Restaurant not found: " + menuItem.getRestaurant().getId());
        }
        return menuItem;
    }

    private CartItem requireItem(Cart cart, Long cartItemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + cartItemId));
    }

    /** Drops items whose menu item is no longer available, or clears the whole cart if its restaurant is no longer visible. */
    private void reconcile(Cart cart) {
        if (cart.getRestaurant() != null && !RestaurantService.isCustomerVisible(cart.getRestaurant())) {
            cartItemRepository.deleteByCartId(cart.getId());
            cart.getItems().clear();
            cart.setRestaurant(null);
            cartRepository.save(cart);
            return;
        }

        List<CartItem> stale = cart.getItems().stream()
                .filter(item -> !item.getMenuItem().isAvailable())
                .toList();
        if (!stale.isEmpty()) {
            cartItemRepository.deleteAllInBatch(stale);
            cart.getItems().removeAll(stale);
            if (cart.getItems().isEmpty()) {
                cart.setRestaurant(null);
                cartRepository.save(cart);
            }
        }
    }

    private Cart loadOrCreateCart() {
        Long customerId = userContext.getCurrentUserId();
        return cartRepository.findByCustomerIdWithItems(customerId)
                .orElseGet(() -> createCart(customerId));
    }

    /**
     * Same as {@link #loadOrCreateCart()} but for callers about to mutate the cart: takes the
     * pessimistic write lock first, then loads the item graph, so the lock is already held by
     * the time any item is read or written. If no cart row exists yet there is nothing to lock,
     * so this falls back to plain creation — a race between two first-ever "add to cart" calls
     * for a brand-new customer is bounded by the unique constraint on {@code customer_id} rather
     * than by this lock.
     */
    private Cart loadOrCreateCartForUpdate() {
        Long customerId = userContext.getCurrentUserId();
        if (cartRepository.findByCustomerIdForUpdate(customerId).isEmpty()) {
            return createCart(customerId);
        }
        return cartRepository.findByCustomerIdWithItems(customerId).orElseThrow();
    }

    private Cart createCart(Long customerId) {
        Cart cart = new Cart();
        cart.setCustomer(userRepository.getReferenceById(customerId));
        return cartRepository.save(cart);
    }
}
