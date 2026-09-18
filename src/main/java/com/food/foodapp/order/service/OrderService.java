package com.food.foodapp.order.service;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.repository.AddressRepository;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.cart.repository.CartItemRepository;
import com.food.foodapp.cart.repository.CartRepository;
import com.food.foodapp.common.exception.AccountSuspendedException;
import com.food.foodapp.common.exception.AddressNotFoundException;
import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.InvalidOrderStatusTransitionException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.MaintenanceModeException;
import com.food.foodapp.common.exception.MenuItemUnavailableException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.order.dto.CheckoutRequest;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.DeliveryDashboardResponse;
import com.food.foodapp.order.dto.OrderDeliveryResponse;
import com.food.foodapp.order.dto.OrderListResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderSummaryResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerOrderStatsResponse;
import com.food.foodapp.order.dto.OwnerOrderSummaryResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.entity.DeliveryConfirmedBy;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.entity.RevenueTransaction;
import com.food.foodapp.order.entity.RevenueTransactionType;
import com.food.foodapp.order.mapper.OrderMapper;
import com.food.foodapp.order.repository.OrderItemCount;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.order.repository.RevenueTransactionRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import com.food.foodapp.restaurant.service.RestaurantOwnershipGuard;
import com.food.foodapp.restaurant.service.RestaurantService;
import com.food.foodapp.settings.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
     * Statuses an owner may explicitly request via {@link #updateOrderStatus}: the kitchen
     * progression ({@code PREPARING}, {@code READY_FOR_DELIVERY}) and cancellation. {@code
     * CONFIRMED} is the only-ever-initial status (see {@link OrderStatus}), so it is never a valid
     * explicit target. {@code OUT_FOR_DELIVERY} and {@code DELIVERED} are both excluded too, but for
     * a different reason than {@code CONFIRMED}: each has its own dedicated action with side effects
     * this generic status update doesn't perform — dispatch stamps {@code sentToDeliveryAt} and an
     * optional courier name (see {@link #sendToDelivery}), and delivery confirmation stamps {@code
     * deliveredAt}/{@code deliveredBy} and atomically recognizes revenue (see {@link #deliverOrder}
     * and {@link #confirmDelivery}). Routing those two through this method would let either happen
     * without its required side effects.
     */
    private static final Set<OrderStatus> OWNER_REQUESTABLE_STATUSES =
            Set.of(OrderStatus.PREPARING, OrderStatus.READY_FOR_DELIVERY, OrderStatus.CANCELLED);

    /**
     * Statuses an owner may filter the order list by. Unlike {@link #OWNER_REQUESTABLE_STATUSES},
     * every {@link OrderStatus} value is a legal filter here — there is no internal, non-resting
     * status to exclude (contrast {@link #resolveCustomerStatusFilter}, which is equally
     * permissive for the same reason).
     */
    private static final Set<OrderStatus> OWNER_LISTABLE_STATUSES =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_DELIVERY,
                    OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DASHBOARD_RECENT_ORDERS_LIMIT = 5;

    /** Wide-open sentinel bounds {@link #listOrdersForCustomer} resolves an absent fromDate/toDate to — see {@link OrderRepository#findByCustomerIdWithFilters}. */
    private static final LocalDateTime EARLIEST_POSSIBLE_ORDER_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime LATEST_POSSIBLE_ORDER_DATE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserContext userContext;
    private final RestaurantOwnershipGuard ownershipGuard;
    private final OrderAnalyticsService orderAnalyticsService;
    private final PlatformSettingsService platformSettingsService;
    private final RevenueTransactionRepository revenueTransactionRepository;

    @Transactional(readOnly = true)
    public CheckoutResponse previewCheckout(CheckoutRequest request) {
        Long customerId = userContext.getCurrentUserId();
        Cart cart = requireNonEmptyCart(cartRepository.findByCustomerIdWithItems(customerId).orElse(null));

        OrderComputation computation = computeOrder(request, cart, customerId);
        return OrderMapper.toCheckoutResponse(computation.restaurant(), computation.items(), computation.address(),
                computation.paymentMethod(), computation.subtotal(), computation.deliveryFee(),
                computation.total());
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

    /**
     * The customer's order-history table: paginated, newest first, optionally narrowed by status,
     * restaurant, and/or a {@code createdAt} date range. Unlike {@link #listOrdersForOwner}, every
     * {@link OrderStatus} value is a valid filter here — this is the customer's own complete
     * history, not the owner dashboard's tab set, so there's no reason to hide {@code CONFIRMED} or
     * {@code CANCELLED} orders from it.
     * <p>
     * {@code fromDate}/{@code toDate} are calendar days in the caller's request, not timestamps;
     * an absent bound is resolved to {@link #EARLIEST_POSSIBLE_ORDER_DATE}/
     * {@link #LATEST_POSSIBLE_ORDER_DATE} rather than left {@code null}, and a present one is
     * converted to a {@code [fromDate 00:00, toDate+1 00:00)} range so {@code toDate} is inclusive
     * of the whole day — see {@link OrderRepository#findByCustomerIdWithFilters} for why these two
     * are never passed through as {@code null}.
     * <p>
     * Item counts are fetched in one extra query for the whole page (see
     * {@link OrderRepository#sumItemQuantitiesByOrderIds}) rather than a fetch join, to avoid a
     * to-many join multiplying/breaking the paginated result.
     */
    @Transactional(readOnly = true)
    public OrderListResponse listOrdersForCustomer(String rawStatus, Long restaurantId, LocalDate fromDate,
                                                     LocalDate toDate, int page, int size) {
        Long customerId = userContext.getCurrentUserId();
        validatePagination(page, size);
        OrderStatus status = resolveCustomerStatusFilter(rawStatus);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidRequestParameterException("Query parameter 'fromDate' must not be after 'toDate'");
        }
        LocalDateTime from = fromDate == null ? EARLIEST_POSSIBLE_ORDER_DATE : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? LATEST_POSSIBLE_ORDER_DATE : toDate.plusDays(1).atStartOfDay();

        Page<Order> result = orderRepository.findByCustomerIdWithFilters(
                customerId, status, restaurantId, from, to, PageRequest.of(page, size));

        List<Long> orderIds = result.getContent().stream().map(Order::getId).toList();
        Map<Long, Long> itemCounts = orderIds.isEmpty() ? Map.of() : orderRepository
                .sumItemQuantitiesByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(OrderItemCount::orderId, OrderItemCount::itemCount));

        List<OrderSummaryResponse> summaries = result.getContent().stream()
                .map(order -> OrderMapper.toSummary(order, itemCounts.getOrDefault(order.getId(), 0L)))
                .toList();

        return OrderListResponse.builder()
                .orders(summaries)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * A customer may only cancel an order while it is still {@code CONFIRMED} — once the
     * restaurant has moved it to {@code PREPARING} (or any later status), cancellation is refused
     * here even though {@link OrderStatus#canTransitionTo} still allows {@code PREPARING} and
     * {@code READY_FOR_DELIVERY} to reach {@code CANCELLED}; that broader table is what the
     * restaurant owner's own cancellation path ({@link #updateOrderStatus}) still relies on, so
     * this narrower rule is enforced here rather than in the shared transition table.
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Long customerId = userContext.getCurrentUserId();
        Order order = requireOwnedOrder(orderId, customerId);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStatusTransitionException(
                    "Order " + order.getId() + " cannot be cancelled after preparation has started");
        }

        transitionStatus(order, OrderStatus.CANCELLED);
        return OrderMapper.toResponse(order);
    }

    /**
     * PUT /orders/{id}/confirm-delivery — the customer's own acknowledgement that an
     * {@code OUT_FOR_DELIVERY} order has actually arrived. Scoped to the caller via
     * {@link OrderRepository#findByIdAndCustomerIdForUpdate} the same way {@link #requireOwnedOrder}
     * scopes every other customer-facing lookup — an order that exists but belongs to another
     * customer is indistinguishable from one that doesn't exist at all (404, not 403) — but this one
     * additionally takes a {@code PESSIMISTIC_WRITE} row lock: since {@link #deliverOrder} gives the
     * restaurant an independent path to the same {@code DELIVERED} target, the lock is what
     * guarantees the two can never both "win" against a concurrent call on the same order (see
     * {@link OrderRepository#findByIdAndCustomerIdForUpdate}). {@link #transitionStatus} then
     * enforces both "must currently be {@code OUT_FOR_DELIVERY}" and "cannot already be {@code
     * DELIVERED}" via the single transition table in {@link OrderStatus} — there is no separate
     * re-check for either rule here.
     * <p>
     * The status flip and the {@code deliveredAt}/{@code deliveredBy} fields are written in this one
     * {@code @Transactional} method, so they can never disagree. No separate "add to the owner's
     * revenue" step exists here — unlike {@link #deliverOrder}, this path does not itself write a
     * {@link RevenueTransaction} row, but revenue is still recognized correctly either way:
     * {@code OrderAnalyticsService}/the owner dashboard compute revenue on demand as a live
     * {@code SUM} over {@code DELIVERED} orders (see {@code OrderAnalyticsService}'s class javadoc),
     * so the instant this transaction commits, every subsequent read of the owner's dashboard or
     * revenue analytics already reflects this order's total — counted exactly once, since a second
     * confirmation attempt (from either path) is rejected before ever reaching here.
     */
    @Transactional
    public OrderResponse confirmDelivery(Long orderId) {
        Long customerId = userContext.getCurrentUserId();
        Order order = orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        transitionStatus(order, OrderStatus.DELIVERED, () -> {
            order.setDeliveredAt(LocalDateTime.now());
            order.setDeliveredBy(DeliveryConfirmedBy.CUSTOMER);
        });

        return OrderMapper.toResponse(order);
    }

    /**
     * POST /owner/restaurants/{restaurantId}/orders/{orderId}/send-to-delivery — the restaurant
     * dispatching a {@code READY_FOR_DELIVERY} order to a courier. Stamps {@code sentToDeliveryAt}
     * and, if supplied, {@code deliveryPersonName} in the same write as the {@code
     * OUT_FOR_DELIVERY} status flip, via the same {@link #transitionStatus} choke point every other
     * status change routes through — an order that isn't currently {@code READY_FOR_DELIVERY}
     * (including one already dispatched) is rejected by {@link OrderStatus#canTransitionTo} before
     * either field is touched.
     * <p>
     * Takes the same {@code PESSIMISTIC_WRITE} lock {@link #deliverOrder} does, via
     * {@link OrderRepository#findByIdAndRestaurantIdForUpdate} — not strictly required for
     * correctness here (dispatch has no revenue side effect to protect), but keeps every owner-side
     * order mutation that isn't the general {@link #updateOrderStatus} on the same locking
     * convention.
     */
    @Transactional
    public OrderResponse sendToDelivery(Long restaurantId, Long orderId, String deliveryPersonName) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        Order order = orderRepository.findByIdAndRestaurantIdForUpdate(orderId, restaurantId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        transitionStatus(order, OrderStatus.OUT_FOR_DELIVERY, () -> {
            order.setSentToDeliveryAt(LocalDateTime.now());
            String trimmed = trimToNull(deliveryPersonName);
            if (trimmed != null) {
                order.setDeliveryPersonName(trimmed);
            }
        });

        return OrderMapper.toResponse(order);
    }

    /**
     * POST /owner/restaurants/{restaurantId}/orders/{orderId}/deliver — the restaurant/delivery
     * side confirming an {@code OUT_FOR_DELIVERY} order has been handed to the customer. This is the
     * restaurant-driven counterpart to the customer's own {@link #confirmDelivery}: both reach the
     * same terminal {@code DELIVERED} status through the same {@link OrderStatus} transition table,
     * so whichever call lands first wins and the other is rejected as an illegal transition
     * ({@code 409}, via {@link InvalidOrderStatusTransitionException}) — there is no way for both to
     * independently succeed against the same order.
     * <p>
     * <b>Atomicity and revenue recognition:</b> the status flip (plus {@code deliveredAt}/{@code
     * deliveredBy}) and the {@link RevenueTransaction} insert happen in this one
     * {@code @Transactional} method, so they always commit or roll back together — a failure
     * writing the revenue row (including the database rejecting a duplicate {@code order_id}) rolls
     * back the status change too, and vice versa. The order row is read with
     * {@link OrderRepository#findByIdAndRestaurantIdForUpdate}'s {@code PESSIMISTIC_WRITE} lock,
     * which is what actually prevents two concurrent calls (from either this method or
     * {@link #confirmDelivery}) from both reading {@code OUT_FOR_DELIVERY} and both attempting to
     * recognize revenue; the {@code UNIQUE(order_id)} database constraint on {@code
     * revenue_transactions} (see {@link RevenueTransaction}) is the defense-in-depth backstop behind
     * that lock, translated here into the same {@code 409} rather than a raw {@code 500}.
     *
     * @throws com.food.foodapp.common.exception.OrderNotFoundException      404 — no such order for this restaurant
     * @throws com.food.foodapp.common.exception.OwnerAccessDeniedException  403 — caller does not own {@code restaurantId}
     * @throws InvalidOrderStatusTransitionException                        409 — order is not currently {@code OUT_FOR_DELIVERY} (including if it was already delivered)
     */
    @Transactional
    public OrderDeliveryResponse deliverOrder(Long restaurantId, Long orderId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        Order order = orderRepository.findByIdAndRestaurantIdForUpdate(orderId, restaurantId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        transitionStatus(order, OrderStatus.DELIVERED, () -> {
            order.setDeliveredAt(LocalDateTime.now());
            order.setDeliveredBy(DeliveryConfirmedBy.OWNER);
        });

        RevenueTransaction revenue = recordRevenue(order);
        return OrderMapper.toOrderDeliveryResponse(order, revenue);
    }

    /**
     * Writes the one {@link RevenueTransaction} row for a just-delivered order.
     * {@code saveAndFlush} (rather than {@code save}) forces the {@code UNIQUE(order_id)}
     * constraint to be checked synchronously, inside {@link #deliverOrder}, so a violation surfaces
     * here as a clean {@code 409} instead of an opaque failure at eventual transaction-commit time.
     */
    private RevenueTransaction recordRevenue(Order order) {
        RevenueTransaction revenue = new RevenueTransaction();
        revenue.setOrder(order);
        revenue.setRestaurant(order.getRestaurant());
        revenue.setAmount(order.getTotal());
        revenue.setType(RevenueTransactionType.ORDER_PAYMENT);
        try {
            return revenueTransactionRepository.saveAndFlush(revenue);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidOrderStatusTransitionException(
                    "Order " + order.getId() + " has already been delivered");
        }
    }

    /**
     * GET /owner/restaurants/{restaurantId}/orders/delivery-dashboard — the owner dashboard's
     * Delivery Orders section: the four summary KPI cards plus the operational queue of every order
     * currently {@code OUT_FOR_DELIVERY}. {@code deliveredTodayCount}/{@code deliveredTodayRevenue}
     * reuse the exact same {@code SUM(total) WHERE status = DELIVERED} accounting
     * {@code OrderAnalyticsService} uses everywhere else, scoped to the server's current calendar
     * day, so this can never disagree with the rest of the dashboard about what counts as revenue.
     */
    @Transactional(readOnly = true)
    public DeliveryDashboardResponse getDeliveryDashboard(Long restaurantId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        List<Order> activeOrders =
                orderRepository.findByRestaurantIdAndStatusWithItems(restaurantId, OrderStatus.OUT_FOR_DELIVERY);

        LocalDate today = LocalDate.now();
        RevenueAggregate deliveredToday = orderRepository.sumRevenueByRestaurantAndStatusInRange(
                restaurantId, OrderStatus.DELIVERED, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return OrderMapper.toDeliveryDashboard(activeOrders, deliveredToday);
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
     * Authorized by {@code RestaurantOwnershipGuard.requireOwnedRestaurant(restaurantId)} at the
     * top of the method — a caller who is not the restaurant's owner gets {@code 403} before any
     * order is loaded, and an anonymous caller {@code 401} at the filter chain.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long restaurantId, Long orderId, String rawStatus) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        Order order = orderRepository.findByIdAndRestaurantIdWithItems(orderId, restaurantId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        OrderStatus target = resolveOwnerTargetStatus(rawStatus);

        transitionStatus(order, target);

        return OrderMapper.toResponse(order);
    }

    /**
     * The owner dashboard's paginated, status-tabbed orders table. Authorized by
     * {@code RestaurantOwnershipGuard.requireOwnedRestaurant} the same way {@link #updateOrderStatus}
     * is; an unknown restaurant id fails with {@link RestaurantNotFoundException} rather than a
     * silently-empty page.
     */
    @Transactional(readOnly = true)
    public OwnerOrderListResponse listOrdersForOwner(Long restaurantId, String rawStatus, int page, int size) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        validatePagination(page, size);
        OrderStatus status = resolveOwnerListableStatus(rawStatus);

        Page<Order> result = orderRepository.findByRestaurantIdAndOptionalStatus(
                restaurantId, status, PageRequest.of(page, size));

        return OwnerOrderListResponse.builder()
                .orders(toOwnerSummaries(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /** The owner order-detail view, scoped to {@code restaurantId} the same way {@link #updateOrderStatus} is. */
    @Transactional(readOnly = true)
    public OwnerOrderResponse getOrderForOwner(Long restaurantId, Long orderId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        Order order = orderRepository.findByIdAndRestaurantIdWithItems(orderId, restaurantId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return OrderMapper.toOwnerResponse(order);
    }

    /**
     * The combined stats + recent-orders + month-KPI + revenue-series payload behind the owner
     * dashboard's landing view — see {@link OwnerDashboardResponse}. The finer-grained, fully
     * paginated/filterable order list this is layered on top of is {@link #listOrdersForOwner}.
     * <p>
     * This {@code {restaurantId}}-scoped form authorizes via
     * {@code RestaurantOwnershipGuard.requireOwnedRestaurant} (a caller who does not own the
     * restaurant gets {@code 403}); the no-argument {@link #getDashboard()} resolves the caller's
     * own restaurant instead, which needs no further ownership check.
     */
    @Transactional(readOnly = true)
    public OwnerDashboardResponse getDashboard(Long restaurantId) {
        Restaurant restaurant = ownershipGuard.requireOwnedRestaurant(restaurantId);
        return buildDashboard(restaurant);
    }

    /**
     * The no-path-variable {@code GET /owner/dashboard}: resolves the caller's own restaurant
     * (owner registration creates exactly one — see {@code RestaurantRepository#findByOwnerId})
     * and builds the same payload as {@link #getDashboard(Long)}. The lookup is itself the
     * authorization — the result can only ever be the caller's own restaurant — so no
     * {@code RestaurantOwnershipGuard} call is needed here. An anonymous caller is already
     * rejected with {@code 401} at the security filter chain ({@code /owner/**} is authenticated).
     *
     * @throws RestaurantNotFoundException if no restaurant is owned by the caller ({@code 404})
     */
    @Transactional(readOnly = true)
    public OwnerDashboardResponse getDashboard() {
        Long ownerId = userContext.getCurrentUserId();
        Restaurant restaurant = restaurantRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new RestaurantNotFoundException(
                        "No restaurant is associated with your account"));
        return buildDashboard(restaurant);
    }

    private OwnerDashboardResponse buildDashboard(Restaurant restaurant) {
        Long restaurantId = restaurant.getId();

        OwnerOrderStatsResponse stats = OwnerOrderStatsResponse.builder()
                .confirmedCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.CONFIRMED))
                .deliveredCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.DELIVERED))
                .cancelledCount(orderRepository.countByRestaurantIdAndStatus(restaurantId, OrderStatus.CANCELLED))
                .totalCount(orderRepository.countByRestaurantId(restaurantId))
                .build();

        Pageable recentPageable = PageRequest.of(0, DASHBOARD_RECENT_ORDERS_LIMIT);
        List<OwnerOrderSummaryResponse> recentOrders = toOwnerSummaries(orderRepository
                .findByRestaurantIdAndOptionalStatus(restaurantId, null, recentPageable).getContent());

        // Delegated verbatim to OrderAnalyticsService — the same figures its /analytics/overview
        // and /analytics/revenue endpoints expose (revenue with no range = the current
        // Saturday-to-Friday week, seven zero-filled points, week-over-week change). Each call
        // re-runs its own ownership guard; harmless here since the caller already owns this
        // restaurant.
        OwnerAnalyticsOverviewResponse overview = orderAnalyticsService.getOverview(restaurantId);
        OwnerRevenueAnalyticsResponse revenue = orderAnalyticsService.getRevenue(restaurantId, null, null);

        return OrderMapper.toDashboard(restaurant, stats, recentOrders, overview, revenue);
    }

    /**
     * Maps a page of owner orders to summary rows, resolving every row's {@code itemCount} in one
     * batch query for the whole page (see {@link OrderRepository#sumItemQuantitiesByOrderIds}) —
     * the same no-N+1 approach {@link #listOrdersForCustomer} uses for the customer history table.
     */
    private List<OwnerOrderSummaryResponse> toOwnerSummaries(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> itemCounts = orderIds.isEmpty() ? Map.of() : orderRepository
                .sumItemQuantitiesByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(OrderItemCount::orderId, OrderItemCount::itemCount));
        return orders.stream()
                .map(order -> OrderMapper.toOwnerSummary(order, itemCounts.getOrDefault(order.getId(), 0L)))
                .toList();
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

    /** Unlike {@link #resolveOwnerListableStatus}, every {@link OrderStatus} value is allowed — see {@link #listOrdersForCustomer}. */
    private OrderStatus resolveCustomerStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestParameterException("Invalid 'status' value: '" + raw + "'");
        }
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
        transitionStatus(order, target, () -> { });
    }

    /**
     * Same as {@link #transitionStatus(Order, OrderStatus)}, plus a hook run only once the
     * transition is confirmed legal and only just before the one {@code save} — see
     * {@link #confirmDelivery}, which uses this to stamp {@code deliveredAt} in the same write as
     * the status flip, so a rejected transition (still {@code OUT_FOR_DELIVERY} required, or
     * already {@code DELIVERED}) can never leave a {@code deliveredAt} behind on an order that
     * didn't actually just get delivered.
     */
    private void transitionStatus(Order order, OrderStatus target, Runnable beforeSave) {
        if (!order.getStatus().canTransitionTo(target)) {
            throw new InvalidOrderStatusTransitionException(
                    "Order " + order.getId() + " cannot move from " + order.getStatus() + " to " + target);
        }
        beforeSave.run();
        order.setStatus(target);
        orderRepository.save(order);
    }

    private Order requireOwnedOrder(Long orderId, Long customerId) {
        return orderRepository.findByIdAndCustomerIdWithItems(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    private void requireActiveCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new UnauthenticatedException("Authentication required"));
        if (customer.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException("This account has been suspended");
        }
    }

    /**
     * Shared by {@link #previewCheckout} and {@link #placeOrder}: re-validates restaurant
     * availability, re-validates every item is still orderable, re-reads authoritative prices
     * from the already-freshly-loaded {@code cart}, resolves the delivery address (a saved address
     * that must belong to the caller, or a transient inline one — see
     * {@link #resolveDeliveryAddress}), and validates the payment method — then recomputes
     * subtotal/delivery/total from that, never from anything the caller sent.
     * <p>
     * Also the single choke point for {@link PlatformSettingsService#isMaintenanceModeEnabled()}
     * and for the customer's {@link UserStatus}: both {@link #previewCheckout} and
     * {@link #placeOrder} refuse to proceed while the admin has maintenance mode enabled, or once
     * an admin has suspended the calling customer's account — even for a session whose JWT was
     * issued before the suspension.
     */
    private OrderComputation computeOrder(CheckoutRequest request, Cart cart, Long customerId) {
        if (platformSettingsService.isMaintenanceModeEnabled()) {
            throw new MaintenanceModeException("Ordering is temporarily disabled for maintenance");
        }
        requireActiveCustomer(customerId);

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

        Address address = resolveDeliveryAddress(request, customerId);
        PaymentMethod paymentMethod = resolvePaymentMethod(request.getPaymentMethod());

        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getMenuItem().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = restaurant.getDeliveryFee();
        BigDecimal total = subtotal.add(deliveryFee);

        return new OrderComputation(restaurant, cart.getItems(), address, paymentMethod, subtotal, deliveryFee,
                total);
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
        order.setTotal(computation.total());
        order.setPaymentMethod(computation.paymentMethod());
        order.setStatus(OrderStatus.CONFIRMED);

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

    /**
     * Accepts {@code "CASH_ON_DELIVERY"}, {@code "COD"} and the frontend's {@code "cash"}
     * (all case-insensitive) as aliases for the one supported method. The persisted enum value is
     * always {@link PaymentMethod#CASH_ON_DELIVERY}.
     */
    private PaymentMethod resolvePaymentMethod(String raw) {
        if (raw != null && (raw.equalsIgnoreCase("CASH_ON_DELIVERY")
                || raw.equalsIgnoreCase("COD")
                || raw.equalsIgnoreCase("CASH"))) {
            return PaymentMethod.CASH_ON_DELIVERY;
        }
        throw new InvalidRequestParameterException(
                "Invalid 'paymentMethod' value: '" + raw + "'. Only cash on delivery is currently supported.");
    }

    /**
     * Resolves the checkout's delivery destination to an {@link Address} the rest of the flow can
     * read uniformly:
     * <ul>
     *   <li>{@code addressId} present -> the caller's saved address, 404 if it isn't one they own;</li>
     *   <li>otherwise -> a transient, <b>never-persisted</b> {@link Address} populated from the
     *       request's inline {@code street/city/postalCode/notes/label}. It is only a data carrier
     *       for {@link #buildOrder}, which snapshots it onto the order's flat {@code delivery_*}
     *       columns; no {@code addresses} row is ever created.</li>
     * </ul>
     * The "exactly one of the two" rule is enforced at the request boundary
     * ({@link CheckoutRequest#isExactlyOneDeliveryTargetPresent()}); the inline-completeness check
     * here is a defensive backstop for direct service callers.
     */
    private Address resolveDeliveryAddress(CheckoutRequest request, Long customerId) {
        if (request.getAddressId() != null) {
            return addressRepository.findByIdAndCustomerId(request.getAddressId(), customerId)
                    .orElseThrow(() -> new AddressNotFoundException(
                            "Address not found: " + request.getAddressId()));
        }
        if (isBlank(request.getStreet()) || isBlank(request.getCity())) {
            throw new InvalidRequestParameterException(
                    "Provide either a saved addressId or an inline address with at least street and city");
        }
        Address inline = new Address();
        inline.setLabel(trimToNull(request.getLabel()));
        inline.setStreet(request.getStreet().trim());
        inline.setCity(request.getCity().trim());
        inline.setPostalCode(trimToNull(request.getPostalCode()));
        inline.setNotes(trimToNull(request.getNotes()));
        return inline;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                                     BigDecimal total) {
    }
}
