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
import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.service.CouponService;
import com.food.foodapp.coupon.service.CouponService.CouponApplication;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.order.dto.CheckoutRequest;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerOrderStatsResponse;
import com.food.foodapp.order.dto.OwnerOrderSummaryResponse;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.mapper.OrderMapper;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * <p>
 * When {@link CheckoutRequest#getCouponCode()} is set, {@link #placeOrder} records the redemption
 * via {@code CouponService#recordUsage} right after the order is saved, in the same transaction —
 * that call takes its own row lock on the coupon (not the cart lock above) so two different
 * customers racing to redeem the last remaining use of a limited coupon can't both succeed.
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

    /**
     * Statuses an owner may filter the order list by — exactly the owner dashboard's tabs besides
     * "all" (see the task's canonical tab set). {@code CONFIRMED} has no tab of its own (it's an
     * internal, effectively-instantaneous transition — see {@link OrderStatus}) and neither does
     * {@code CANCELLED}; a {@code null}/absent filter (the "all" tab) still returns every status,
     * including those two.
     */
    private static final Set<OrderStatus> OWNER_LISTABLE_STATUSES =
            Set.of(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.ON_THE_WAY, OrderStatus.DELIVERED);

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DASHBOARD_RECENT_ORDERS_LIMIT = 5;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final UserContext userContext;
    private final RestaurantService restaurantService;
    private final CouponService couponService;

    @Transactional(readOnly = true)
    public CheckoutResponse previewCheckout(CheckoutRequest request) {
        Long customerId = userContext.getCurrentUserId();
        Cart cart = requireNonEmptyCart(cartRepository.findByCustomerIdWithItems(customerId).orElse(null));

        OrderComputation computation = computeOrder(request, cart, customerId);
        return OrderMapper.toCheckoutResponse(computation.restaurant(), computation.items(), computation.address(),
                computation.paymentMethod(), computation.subtotal(), computation.deliveryFee(),
                computation.couponCode(), computation.discount(), computation.total());
    }

    @Transactional
    public OrderResponse placeOrder(CheckoutRequest request) {
        Long customerId = userContext.getCurrentUserId();
        Cart cart = requireNonEmptyCart(lockCart(customerId));

        OrderComputation computation = computeOrder(request, cart, customerId);
        Order order = buildOrder(computation, customerId);
        Order saved = orderRepository.save(order);

        if (computation.coupon() != null) {
            couponService.recordUsage(computation.coupon(), saved);
        }
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

    /**
     * The owner dashboard's paginated, status-tabbed orders table. Scoped to {@code restaurantId}
     * the same way {@link #updateOrderStatus} is (see its javadoc for the authorization gap this
     * shares) — existence of the restaurant is validated so an unknown id fails with
     * {@link RestaurantNotFoundException} rather than a silently-empty page.
     */
    @Transactional(readOnly = true)
    public OwnerOrderListResponse listOrdersForOwner(Long restaurantId, String rawStatus, int page, int size) {
        restaurantService.requireRestaurant(restaurantId);
        validatePagination(page, size);
        OrderStatus status = resolveOwnerListableStatus(rawStatus);

        Page<Order> result = orderRepository.findByRestaurantIdAndOptionalStatus(
                restaurantId, status, PageRequest.of(page, size));

        return OwnerOrderListResponse.builder()
                .orders(result.getContent().stream().map(OrderMapper::toOwnerSummary).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /** The owner order-detail view, scoped to {@code restaurantId} the same way {@link #updateOrderStatus} is. */
    @Transactional(readOnly = true)
    public OwnerOrderResponse getOrderForOwner(Long restaurantId, Long orderId) {
        Order order = orderRepository.findByIdAndRestaurantIdWithItems(orderId, restaurantId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return OrderMapper.toOwnerResponse(order);
    }

    /**
     * The combined stats + recent-orders payload behind the owner dashboard's landing view — see
     * {@link OwnerDashboardResponse}. The finer-grained, fully paginated/filterable order list
     * this is layered on top of is {@link #listOrdersForOwner}.
     */
    @Transactional(readOnly = true)
    public OwnerDashboardResponse getDashboard(Long restaurantId) {
        Restaurant restaurant = restaurantService.requireRestaurant(restaurantId);

        OwnerOrderStatsResponse stats = OwnerOrderStatsResponse.builder()
                .newCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.NEW))
                .preparingCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.PREPARING))
                .onTheWayCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.ON_THE_WAY))
                .deliveredCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.DELIVERED))
                .totalCount(orderRepository.countByRestaurantId(restaurantId))
                .build();

        Pageable recentPageable = PageRequest.of(0, DASHBOARD_RECENT_ORDERS_LIMIT);
        List<OwnerOrderSummaryResponse> recentOrders = orderRepository
                .findByRestaurantIdAndOptionalStatus(restaurantId, null, recentPageable)
                .getContent().stream().map(OrderMapper::toOwnerSummary).toList();

        return OrderMapper.toDashboard(restaurant, stats, recentOrders);
    }

    private OrderStatus resolveOwnerListableStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestParameterException("Invalid 'status' value: '" + raw + "'");
        }
        if (!OWNER_LISTABLE_STATUSES.contains(status)) {
            throw new InvalidRequestParameterException(
                    "Invalid 'status' value: '" + raw + "'. Allowed values: " + OWNER_LISTABLE_STATUSES);
        }
        return status;
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestParameterException("Query parameter 'page' must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestParameterException(
                    "Query parameter 'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
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

        Coupon coupon = null;
        String couponCode = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            CouponApplication application = couponService.validate(request.getCouponCode(), restaurant, subtotal);
            coupon = application.coupon();
            couponCode = coupon.getCode();
            discount = application.discount();
        }
        BigDecimal total = subtotal.add(deliveryFee).subtract(discount);

        return new OrderComputation(restaurant, cart.getItems(), address, paymentMethod, subtotal, deliveryFee,
                coupon, couponCode, discount, total);
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
        order.setCouponCode(computation.couponCode());
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
                                     Coupon coupon, String couponCode, BigDecimal discount, BigDecimal total) {
    }
}
