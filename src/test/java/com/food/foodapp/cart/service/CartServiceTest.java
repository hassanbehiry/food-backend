package com.food.foodapp.cart.service;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.dto.CartAddItemRequest;
import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.dto.CartSyncItemRequest;
import com.food.foodapp.cart.dto.CartSyncRequest;
import com.food.foodapp.cart.dto.CartUpdateItemRequest;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
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
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserContext userContext;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, menuItemRepository, userRepository, userContext);
        when(userContext.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void getCart_createsEmptyCart_whenNoneExists() {
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.getCart();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCart_reconciles_dropsUnavailableItems() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        CartItem available = cartItem(cart, menuItem(10L, restaurant, true), 1);
        CartItem unavailable = cartItem(cart, menuItem(11L, restaurant, false), 1);
        cart.setItems(new ArrayList<>(List.of(available, unavailable)));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCart();

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getMenuItemId()).isEqualTo(10L);
        verify(cartItemRepository).deleteAllInBatch(List.of(unavailable));
    }

    @Test
    void getCart_reconciles_clearsWholeCart_whenRestaurantNoLongerVisible() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        restaurant.setOpenForOrders(true);
        Cart cart = existingCart(restaurant);
        cart.setItems(new ArrayList<>(List.of(cartItem(cart, menuItem(10L, restaurant, true), 1))));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCart();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getRestaurantId()).isNull();
        verify(cartItemRepository).deleteByCartId(cart.getId());
    }

    @Test
    void addItem_addsNewItem_toEmptyCart() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        MenuItem menuItem = menuItem(10L, restaurant, true);
        when(menuItemRepository.findById(10L)).thenReturn(Optional.of(menuItem));

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(10L);
        request.setQuantity(2);

        CartResponse response = cartService.addItem(request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getRestaurantId()).isEqualTo(restaurant.getId());
    }

    @Test
    void addItem_incrementsQuantity_whenItemAlreadyInCart() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        MenuItem menuItem = menuItem(10L, restaurant, true);
        CartItem existingItem = cartItem(cart, menuItem, 2);
        cart.setItems(new ArrayList<>(List.of(existingItem)));
        stubForUpdate(cart);
        when(menuItemRepository.findById(10L)).thenReturn(Optional.of(menuItem));

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(10L);
        request.setQuantity(3);

        CartResponse response = cartService.addItem(request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void addItem_throwsInvalidRequestParameter_whenMergedQuantityExceedsLimit() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        MenuItem menuItem = menuItem(10L, restaurant, true);
        CartItem existingItem = cartItem(cart, menuItem, CartItem.MAX_QUANTITY_PER_ITEM - 2);
        cart.setItems(new ArrayList<>(List.of(existingItem)));
        stubForUpdate(cart);
        when(menuItemRepository.findById(10L)).thenReturn(Optional.of(menuItem));

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(10L);
        request.setQuantity(3);

        assertThatThrownBy(() -> cartService.addItem(request)).isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void addItem_throwsUnavailable_whenMenuItemNotAvailable() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        when(menuItemRepository.findById(10L)).thenReturn(Optional.of(menuItem(10L, restaurant, false)));

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(10L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(request)).isInstanceOf(MenuItemUnavailableException.class);
    }

    @Test
    void addItem_throwsNotFound_whenMenuItemMissing() {
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        when(menuItemRepository.findById(99L)).thenReturn(Optional.empty());

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(99L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(request)).isInstanceOf(MenuItemNotFoundException.class);
    }

    @Test
    void addItem_throwsRestaurantNotFound_whenRestaurantNotVisible() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        restaurant.setOpenForOrders(true);
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        when(menuItemRepository.findById(10L)).thenReturn(Optional.of(menuItem(10L, restaurant, true)));

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(10L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(request)).isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void addItem_throwsConflict_whenCartHasItemsFromDifferentRestaurant() {
        Restaurant existingRestaurant = visibleRestaurant();
        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(99L);
        otherRestaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        otherRestaurant.setOpenForOrders(true);

        Cart cart = existingCart(existingRestaurant);
        cart.setItems(new ArrayList<>(List.of(cartItem(cart, menuItem(10L, existingRestaurant, true), 1))));
        stubForUpdate(cart);
        when(menuItemRepository.findById(20L)).thenReturn(Optional.of(menuItem(20L, otherRestaurant, true)));

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(20L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(request)).isInstanceOf(CartRestaurantConflictException.class);
    }

    @Test
    void updateItemQuantity_updatesQuantity_whenItemBelongsToCart() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        CartItem item = cartItem(cart, menuItem(10L, restaurant, true), 1);
        item.setId(500L);
        cart.setItems(new ArrayList<>(List.of(item)));
        stubForUpdate(cart);

        CartUpdateItemRequest request = new CartUpdateItemRequest();
        request.setQuantity(7);

        CartResponse response = cartService.updateItemQuantity(500L, request);

        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(7);
    }

    @Test
    void updateItemQuantity_throwsNotFound_whenItemMissing() {
        Cart cart = existingCart(null);
        stubForUpdate(cart);

        CartUpdateItemRequest request = new CartUpdateItemRequest();
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.updateItemQuantity(999L, request))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void removeItem_removesItem_andClearsRestaurant_whenLastItem() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        CartItem item = cartItem(cart, menuItem(10L, restaurant, true), 1);
        item.setId(500L);
        cart.setItems(new ArrayList<>(List.of(item)));
        stubForUpdate(cart);

        CartResponse response = cartService.removeItem(500L);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getRestaurantId()).isNull();
    }

    @Test
    void removeItem_throwsNotFound_whenItemMissing() {
        Cart cart = existingCart(null);
        stubForUpdate(cart);

        assertThatThrownBy(() -> cartService.removeItem(999L)).isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void clearCart_removesAllItemsAndRestaurant() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        cart.setItems(new ArrayList<>(List.of(cartItem(cart, menuItem(10L, restaurant, true), 1))));
        stubForUpdate(cart);

        CartResponse response = cartService.clearCart();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getRestaurantId()).isNull();
        verify(cartItemRepository).deleteByCartId(cart.getId());
    }

    @Test
    void syncCart_replacesItems_withValidSingleRestaurantSelection() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        MenuItem itemA = menuItem(10L, restaurant, true);
        MenuItem itemB = menuItem(11L, restaurant, true);
        when(menuItemRepository.findAllByIdWithRestaurant(anyCollection())).thenReturn(List.of(itemA, itemB));

        CartSyncRequest request = syncRequest(syncItem(10L, 2), syncItem(11L, 1));

        CartResponse response = cartService.syncCart(request);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getRestaurantId()).isEqualTo(restaurant.getId());
        verify(cartItemRepository).deleteByCartId(cart.getId());
    }

    @Test
    void syncCart_clearsCart_whenItemsEmpty() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(restaurant);
        cart.setItems(new ArrayList<>(List.of(cartItem(cart, menuItem(10L, restaurant, true), 1))));
        stubForUpdate(cart);

        CartResponse response = cartService.syncCart(syncRequest());

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getRestaurantId()).isNull();
    }

    @Test
    void syncCart_collapsesDuplicateMenuItemId_lastValueWins() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        MenuItem itemA = menuItem(10L, restaurant, true);
        when(menuItemRepository.findAllByIdWithRestaurant(anyCollection())).thenReturn(List.of(itemA));

        // Two entries for the same menuItemId: the sync payload is the desired end state, so the
        // last qty (5) wins instead of being rejected or summed with the first (1).
        CartSyncRequest request = syncRequest(syncItem(10L, 1), syncItem(10L, 5));

        CartResponse response = cartService.syncCart(request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void syncCart_rejectsMixedRestaurants() {
        Restaurant restaurantA = visibleRestaurant();
        Restaurant restaurantB = new Restaurant();
        restaurantB.setId(99L);
        restaurantB.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurantB.setOpenForOrders(true);
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        when(menuItemRepository.findAllByIdWithRestaurant(anyCollection()))
                .thenReturn(List.of(menuItem(10L, restaurantA, true), menuItem(20L, restaurantB, true)));

        CartSyncRequest request = syncRequest(syncItem(10L, 1), syncItem(20L, 1));

        assertThatThrownBy(() -> cartService.syncCart(request)).isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void syncCart_rejectsUnavailableItem() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        when(menuItemRepository.findAllByIdWithRestaurant(anyCollection()))
                .thenReturn(List.of(menuItem(10L, restaurant, false)));

        CartSyncRequest request = syncRequest(syncItem(10L, 1));

        assertThatThrownBy(() -> cartService.syncCart(request)).isInstanceOf(MenuItemUnavailableException.class);
    }

    @Test
    void syncCart_rejectsMissingMenuItem() {
        Cart cart = existingCart(null);
        stubForUpdate(cart);
        when(menuItemRepository.findAllByIdWithRestaurant(anyCollection())).thenReturn(List.of());

        CartSyncRequest request = syncRequest(syncItem(10L, 1));

        assertThatThrownBy(() -> cartService.syncCart(request)).isInstanceOf(MenuItemNotFoundException.class);
    }

    /** Every mutating operation locks the cart row first, then loads the full item graph. */
    private void stubForUpdate(Cart cart) {
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
    }

    private Cart existingCart(Restaurant restaurant) {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setRestaurant(restaurant);
        return cart;
    }

    private Restaurant visibleRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setDeliveryFee(BigDecimal.valueOf(12));
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurant.setOpenForOrders(true);
        return restaurant;
    }

    private MenuItem menuItem(Long id, Restaurant restaurant, boolean available) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(id);
        menuItem.setRestaurant(restaurant);
        menuItem.setName("Item " + id);
        menuItem.setPrice(BigDecimal.valueOf(25));
        menuItem.setAvailable(available);
        return menuItem;
    }

    private CartItem cartItem(Cart cart, MenuItem menuItem, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setMenuItem(menuItem);
        item.setQuantity(quantity);
        return item;
    }

    private CartSyncRequest syncRequest(CartSyncItemRequest... items) {
        CartSyncRequest request = new CartSyncRequest();
        request.setItems(List.of(items));
        return request;
    }

    private CartSyncItemRequest syncItem(Long menuItemId, int qty) {
        CartSyncItemRequest item = new CartSyncItemRequest();
        item.setMenuItemId(menuItemId);
        item.setQty(qty);
        return item;
    }
}
