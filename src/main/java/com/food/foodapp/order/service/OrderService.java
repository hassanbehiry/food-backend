package com.food.foodapp.order.service;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.repository.AddressRepository;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.cart.repository.CartItemRepository;
import com.food.foodapp.cart.repository.CartRepository;
import com.food.foodapp.common.exception.AddressNotFoundException;
import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.InvalidOrderStatusTransitionException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.MenuItemUnavailableException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.order.dto.CheckoutRequest;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.mapper.OrderMapper;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The checkout/order-creation domain: the two-step flow of a computed, non-persisted preview
 * ({@link #previewCheckout}) followed by an authoritative, persisted order
 * ({@link #placeOrder}), plus order lookup and customer-initiated cancellation.
 * <p>
 * {@link #placeOrder} never trusts anything the caller could have carried over from a prior
 * {@code previewCheckout} response (a total, an availability check, a price) — it re-validates
 * and recomputes everything from scratch via the same {@link #computeOrder} both methods share,
 * because cart contents, menu-item price/availability, and address ownership can all change in
 * the gap between the two calls.
 * <p>
 * {@link #placeOrder} takes the same pessimistic write lock on the cart row that every
 * {@code CartService} mutation does (see {@link CartRepository#findByCustomerIdForUpdate}), and
 * clears the cart only after the order is fully persisted, in the same transaction. That lock
 * doubles as this feature's duplicate-submit guard: this codebase has no Idempotency-Key
 * convention to build on, so a rapid double-tap of "place order" serializes on the same lock a
 * concurrent cart mutation would — by the time the second request acquires it, the first has
 * already committed the order and emptied the cart, so the second sees an empty cart and fails
 * with {@link CartEmptyException} instead of creating a second order.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /**
     * Statuses an owner may explicitly request via {@link #updateOrderStatus}. {@code NEW} is
     * the only-ever-initial status and {@code CONFIRMED} is an internal transition not exposed as
     * its own dashboard tab (see {@link OrderStatus}), so neither is a valid explicit target here.
     */
    private static final Set<OrderStatus> OWNER_REQUESTABLE_STATUSES =
            Set.of(OrderStatus.PREPARING, OrderStatus.ON_THE_WAY, OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final UserContext userContext;

    @Transactional(readOnly = true)
    public CheckoutResponse previewCheckout(CheckoutRequest request) {
        Long customerId = userContext.getCurrentUserId();
        Cart cart = requireNonEmptyCart(cartRepository.findByCustomerIdWithItems(customerId).orElse(null));

        OrderComputation computation = computeOrder(request, cart, customerId);
        return OrderMapper.toCheckoutResponse(computation.restaurant(), computation.items(), computation.address(),
                computation.paymentMethod(), computation.subtotal(), computation.deliveryFee(),
                computation.discount(), computation.total());
    }

    @Transactional
    public OrderResponse placeOrder(CheckoutRequest request) {
        Long customerId = userContext.getCurrentUserId();
        Cart cart = requireNonEmptyCart(lockCart(customerId));

        OrderComputation computation = computeOrder(request, cart, customerId);
        Order order = buildOrder(computation, customerId);
        Order saved = orderRepository.save(order);

        clearCart(cart);

        return OrderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Long customerId = userContext.getCurrentUserId();
        Order order = requireOwnedOrder(orderId, customerId);
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Long customerId = userContext.getCurrentUserId();
        Order order = requireOwnedOrder(orderId, customerId);

        transitionStatus(order, OrderStatus.CANCELLED);
        return OrderMapper.toResponse(order);
    }

    /** GET /orders/{id}/track — always reads live persisted status, never a cached/echoed value. */
    @Transactional(readOnly = true)
    public OrderTrackingResponse trackOrder(Long orderId) {
        Long customerId = userContext.getCurrentUserId();
        Order order = requireOwnedOrder(orderId, customerId);
        return OrderMapper.toTracking(order);
    }

    /**
     * The owner-driven counterpart to {@link #cancelOrder}: both route through the same
     * {@link #transitionStatus} choke point so the legal-transition rules in {@link OrderStatus}
     * are enforced in exactly one place regardless of who initiates the change.
     * <p>
     * Scoped to {@code restaurantId} rather than an authenticated owner — same temporary
     * authorization gap {@code MenuItemService} already has (see {@code OwnerMenuItemController}),
     * to be closed once owner authentication exists.
     * <p>
     * When a {@code NEW} order is asked to move straight to {@code PREPARING}, this advances it
     * through {@code CONFIRMED} first, in the same transaction: the owner dashboard has only one
     * action to take an order out of "New", so accepting it and starting to prepare it are the
     * same request from the caller's point of view even though the state machine still passes
     * through the intermediate status.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long restaurantId, Long orderId, String rawStatus) {
        Order order = orderRepository.findByIdAndRestaurantIdWithItems(orderId, restaurantId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        OrderStatus target = resolveOwnerTargetStatus(rawStatus);

        if (order.getStatus() == OrderStatus.NEW && target == OrderStatus.PREPARING) {
            transitionStatus(order, OrderStatus.CONFIRMED);
        }
        transitionStatus(order, target);

        return OrderMapper.toResponse(order);
    }

    private OrderStatus resolveOwnerTargetStatus(String raw) {
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestParameterException("Invalid 'status' value: '" + raw + "'");
        }
        if (!OWNER_REQUESTABLE_STATUSES.contains(target)) {
            throw new InvalidRequestParameterException(
                    "Invalid 'status' value: '" + raw + "'. Allowed values: " + OWNER_REQUESTABLE_STATUSES);
        }
        return target;
    }

    /**
     * The single point every order-status change routes through, so the legal-transition rules
     * in {@link OrderStatus} are enforced in one place regardless of who initiates the change.
     */
    private void transitionStatus(Order order, OrderStatus target) {
        if (!order.getStatus().canTransitionTo(target)) {
            throw new InvalidOrderStatusTransitionException(
                    "Order " + order.getId() + " cannot move from " + order.getStatus() + " to " + target);
        }
        order.setStatus(target);
        orderRepository.save(order);
    }

    private Order requireOwnedOrder(Long orderId, Long customerId) {
        return orderRepository.findByIdAndCustomerIdWithItems(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    /**
     * Shared by {@link #previewCheckout} and {@link #placeOrder}: re-validates restaurant
     * availability, re-validates every item is still orderable, re-reads authoritative prices
     * from the already-freshly-loaded {@code cart}, validates the address belongs to the caller,
     * and validates the payment method — then recomputes subtotal/delivery/discount/total from
     * that, never from anything the caller sent.
     */
    private OrderComputation computeOrder(CheckoutRequest request, Cart cart, Long customerId) {
        Restaurant restaurant = cart.getRestaurant();
        if (restaurant == null || !RestaurantService.isCustomerVisible(restaurant)) {
            throw new RestaurantNotFoundException("Restaurant not found");
        }
        for (CartItem item : cart.getItems()) {
            if (!item.getMenuItem().isAvailable()) {
                throw new MenuItemUnavailableException(
                        "Menu item " + item.getMenuItem().getId() + " is not currently available");
            }
        }

        Address address = addressRepository.findByIdAndCustomerId(request.getAddressId(), customerId)
                .orElseThrow(() -> new AddressNotFoundException("Address not found: " + request.getAddressId()));
        PaymentMethod paymentMethod = resolvePaymentMethod(request.getPaymentMethod());

        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getMenuItem().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = restaurant.getDeliveryFee();
        // No coupon/discount engine exists yet in this codebase — CartMapper stubs the same field at zero.
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee).subtract(discount);

        return new OrderComputation(restaurant, cart.getItems(), address, paymentMethod, subtotal, deliveryFee,
                discount, total);
    }

    private Order buildOrder(OrderComputation computation, Long customerId) {
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomer(userRepository.getReferenceById(customerId));
        order.setRestaurant(computation.restaurant());

        Address address = computation.address();
        order.setDeliveryLabel(address.getLabel());
        order.setDeliveryStreet(address.getStreet());
        order.setDeliveryCity(address.getCity());
        order.setDeliveryPostalCode(address.getPostalCode());
        order.setDeliveryNotes(address.getNotes());

        order.setSubtotal(computation.subtotal());
        order.setDeliveryFee(computation.deliveryFee());
        order.setDiscount(computation.discount());
        order.setTotal(computation.total());
        order.setPaymentMethod(computation.paymentMethod());
        order.setStatus(OrderStatus.NEW);

        for (CartItem cartItem : computation.items()) {
            MenuItem menuItem = cartItem.getMenuItem();
            BigDecimal lineTotal = menuItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            order.getItems().add(new OrderItem(order, menuItem.getId(), menuItem.getName(), menuItem.getImageUrl(),
                    menuItem.getPrice(), cartItem.getQuantity(), lineTotal));
        }
        return order;
    }

    /** "ORD-20260825-123456" — human-friendly and, at this order volume, unique enough without a formal collision-retry loop; backstopped by the column's DB unique constraint. */
    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomPart = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return "ORD-" + datePart + "-" + randomPart;
    }

    private PaymentMethod resolvePaymentMethod(String raw) {
        if (raw != null && (raw.equalsIgnoreCase("CASH_ON_DELIVERY") || raw.equalsIgnoreCase("COD"))) {
            return PaymentMethod.CASH_ON_DELIVERY;
        }
        throw new InvalidRequestParameterException(
                "Invalid 'paymentMethod' value: '" + raw + "'. Only cash on delivery is currently supported.");
    }

    private Cart requireNonEmptyCart(Cart cart) {
        if (cart == null || cart.getItems().isEmpty()) {
            throw new CartEmptyException("Cart is empty");
        }
        return cart;
    }

    /** Same lock-then-load pattern {@code CartService} uses for mutations, minus the create-if-missing branch: an order can never be placed against a cart that doesn't exist yet. */
    private Cart lockCart(Long customerId) {
        if (cartRepository.findByCustomerIdForUpdate(customerId).isEmpty()) {
            return null;
        }
        return cartRepository.findByCustomerIdWithItems(customerId).orElseThrow();
    }

    /** Mirrors {@code CartService#clearCart}: delete the items, then clear the in-memory collection and unset the restaurant on the already-locked cart. */
    private void clearCart(Cart cart) {
        cartItemRepository.deleteByCartId(cart.getId());
        cartItemRepository.flush();
        cart.getItems().clear();
        cart.setRestaurant(null);
        cartRepository.save(cart);
    }

    private record OrderComputation(Restaurant restaurant, List<CartItem> items, Address address,
                                     PaymentMethod paymentMethod, BigDecimal subtotal, BigDecimal deliveryFee,
                                     BigDecimal discount, BigDecimal total) {
    }
}
