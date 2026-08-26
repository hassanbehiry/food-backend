package com.food.foodapp.order.service;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.repository.AddressRepository;
import com.food.foodapp.auth.entity.User;
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
import com.food.foodapp.common.exception.CouponNotApplicableException;
import com.food.foodapp.common.exception.CouponNotFoundException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.entity.DiscountType;
import com.food.foodapp.coupon.service.CouponService;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.order.dto.CheckoutRequest;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.OrderListResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.repository.OrderItemCount;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.service.RestaurantService;
import com.food.foodapp.common.exception.MaintenanceModeException;
import com.food.foodapp.settings.service.PlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserContext userContext;

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private CouponService couponService;

    @Mock
    private PlatformSettingsService platformSettingsService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(cartRepository, cartItemRepository, addressRepository, userRepository,
                orderRepository, userContext, restaurantService, couponService, platformSettingsService);
        lenient().when(userContext.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void previewCheckout_returnsComputedSummary_withoutPersistingAnything() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));

        CheckoutResponse response = orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY"));

        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
        assertThat(response.getRestaurantId()).isEqualTo(restaurant.getId());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void previewCheckout_throwsMaintenanceMode_whenMaintenanceModeEnabled() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(platformSettingsService.isMaintenanceModeEnabled()).thenReturn(true);

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(MaintenanceModeException.class);
    }

    @Test
    void previewCheckout_throwsCartEmpty_whenCartHasNoItems() {
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void previewCheckout_throwsRestaurantNotFound_whenRestaurantNoLongerVisible() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        restaurant.setOpenForOrders(true);
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void previewCheckout_throwsUnavailable_whenAnItemIsNoLongerAvailable() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setRestaurant(restaurant);
        cart.setItems(new ArrayList<>(List.of(cartItem(menuItem(10L, "Pizza", BigDecimal.valueOf(50), false), 2))));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(MenuItemUnavailableException.class);
    }

    @Test
    void previewCheckout_throwsAddressNotFound_whenAddressNotOwnedByCaller() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(999L, "CASH_ON_DELIVERY")))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void previewCheckout_throwsInvalidRequestParameter_whenPaymentMethodUnsupported() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CREDIT_CARD")))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void previewCheckout_appliesCouponDiscount_whenCouponCodeValid() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));
        Coupon coupon = coupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        when(couponService.validate("SAVE10", restaurant, BigDecimal.valueOf(100)))
                .thenReturn(new CouponService.CouponApplication(coupon, BigDecimal.TEN));

        CheckoutResponse response = orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY", "SAVE10"));

        assertThat(response.getCouponCode()).isEqualTo("SAVE10");
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(102));
    }

    @Test
    void previewCheckout_propagatesCouponNotFound_whenCouponCodeUnknown() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));
        when(couponService.validate("BADCODE", restaurant, BigDecimal.valueOf(100)))
                .thenThrow(new CouponNotFoundException("Coupon not found: BADCODE"));

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY", "BADCODE")))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void placeOrder_persistsOrderAndClearsCart() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });

        OrderResponse response = orderService.placeOrder(checkoutRequest(50L, "CASH_ON_DELIVERY"));

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getOrderNumber()).startsWith("ORD-");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo(BigDecimal.valueOf(100));

        verify(cartItemRepository).deleteByCartId(cart.getId());
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getRestaurant()).isNull();
        verify(couponService, never()).recordUsage(any(), any());
    }

    @Test
    void placeOrder_recordsCouponUsage_andSnapshotsCouponCode_whenCouponApplied() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });
        Coupon coupon = coupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        when(couponService.validate("SAVE10", restaurant, BigDecimal.valueOf(100)))
                .thenReturn(new CouponService.CouponApplication(coupon, BigDecimal.TEN));

        OrderResponse response = orderService.placeOrder(checkoutRequest(50L, "CASH_ON_DELIVERY", "SAVE10"));

        assertThat(response.getCouponCode()).isEqualTo("SAVE10");
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(102));
        verify(couponService).recordUsage(eq(coupon), any(Order.class));
    }

    @Test
    void placeOrder_rollsBackWithoutRecordingUsage_whenCouponNoLongerApplicable() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));
        when(couponService.validate("SAVE10", restaurant, BigDecimal.valueOf(100)))
                .thenThrow(new CouponNotApplicableException("Coupon usage limit has been reached: SAVE10"));

        assertThatThrownBy(() -> orderService.placeOrder(checkoutRequest(50L, "CASH_ON_DELIVERY", "SAVE10")))
                .isInstanceOf(CouponNotApplicableException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_throwsMaintenanceMode_whenMaintenanceModeEnabled() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(platformSettingsService.isMaintenanceModeEnabled()).thenReturn(true);

        assertThatThrownBy(() -> orderService.placeOrder(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(MaintenanceModeException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_throwsCartEmpty_whenNoCartRowExists() {
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(CartEmptyException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_returnsOrder_whenOwnedByCaller() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(700L);

        assertThat(response.getId()).isEqualTo(700L);
    }

    @Test
    void getOrder_throwsNotFound_whenMissingOrNotOwned() {
        when(orderRepository.findByIdAndCustomerIdWithItems(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(999L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelOrder_cancels_whenStillPending() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(700L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_cancels_whenConfirmed() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(700L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_throwsInvalidTransition_oncePreparingHasStarted() {
        Order order = existingOrder(700L, OrderStatus.PREPARING);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    void cancelOrder_throwsNotFound_whenNotOwnedByCaller() {
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void trackOrder_returnsTrackingResponse_whenOwnedByCaller() {
        Order order = existingOrder(700L, OrderStatus.PREPARING);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        OrderTrackingResponse response = orderService.trackOrder(700L);

        assertThat(response.getOrderId()).isEqualTo(700L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    void trackOrder_throwsNotFound_whenNotOwnedByCaller() {
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.trackOrder(700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrdersForCustomer_returnsPaginatedSummaries_withItemCounts() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByCustomerIdWithFilters(
                eq(1L), isNull(), isNull(), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), Pageable.ofSize(20), 1));
        when(orderRepository.sumItemQuantitiesByOrderIds(List.of(700L)))
                .thenReturn(List.of(new OrderItemCount(700L, 3L)));

        OrderListResponse response = orderService.listOrdersForCustomer(null, null, null, null, 0, 20);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getId()).isEqualTo(700L);
        assertThat(response.getOrders().get(0).getRestaurantName()).isEqualTo("Pizza Place");
        assertThat(response.getOrders().get(0).getItemCount()).isEqualTo(3);
    }

    @Test
    void listOrdersForCustomer_defaultsItemCountToZero_whenNoMatchingLinesFound() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByCustomerIdWithFilters(
                eq(1L), isNull(), isNull(), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), Pageable.ofSize(20), 1));
        when(orderRepository.sumItemQuantitiesByOrderIds(List.of(700L))).thenReturn(List.of());

        OrderListResponse response = orderService.listOrdersForCustomer(null, null, null, null, 0, 20);

        assertThat(response.getOrders().get(0).getItemCount()).isZero();
    }

    @Test
    void listOrdersForCustomer_allowsCancelledAsFilterValue_unlikeTheOwnerListing() {
        when(orderRepository.findByCustomerIdWithFilters(
                eq(1L), eq(OrderStatus.CANCELLED), isNull(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(20), 0));

        orderService.listOrdersForCustomer("cancelled", null, null, null, 0, 20);

        verify(orderRepository).findByCustomerIdWithFilters(eq(1L), eq(OrderStatus.CANCELLED), isNull(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    void listOrdersForCustomer_passesRestaurantIdAndInclusiveDateRangeThrough() {
        when(orderRepository.findByCustomerIdWithFilters(
                eq(1L), isNull(), eq(5L), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(20), 0));

        orderService.listOrdersForCustomer(null, 5L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25), 0, 20);

        verify(orderRepository).findByCustomerIdWithFilters(eq(1L), isNull(), eq(5L),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 26, 0, 0)), any(Pageable.class));
    }

    @Test
    void listOrdersForCustomer_rejectsUnknownStatusValue() {
        assertThatThrownBy(() -> orderService.listOrdersForCustomer("SHIPPED", null, null, null, 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForCustomer_rejectsFromDateAfterToDate() {
        assertThatThrownBy(() -> orderService.listOrdersForCustomer(
                null, null, LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 1), 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForCustomer_rejectsInvalidPagination() {
        assertThatThrownBy(() -> orderService.listOrdersForCustomer(null, null, null, null, -1, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForCustomer(null, null, null, null, 0, 0))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForCustomer(null, null, null, null, 0, 51))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_movesNewOrderStraightToPreparing_hoppingThroughConfirmed() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateOrderStatus(5L, 700L, "PREPARING");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderRepository, times(2)).save(order);
    }

    @Test
    void updateOrderStatus_movesPreparingToOnTheWay_directly() {
        Order order = existingOrder(700L, OrderStatus.PREPARING);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateOrderStatus(5L, 700L, "on_the_way");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.ON_THE_WAY);
    }

    @Test
    void updateOrderStatus_rejectsIllegalTransition() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "DELIVERED"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void updateOrderStatus_rejectsUnknownStatusValue() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "SHIPPED"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_rejectsNewAndConfirmedAsExplicitOwnerTargets() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "NEW"))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "CONFIRMED"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_throwsNotFound_whenOrderNotOwnedByRestaurant() {
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "PREPARING"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrdersForOwner_returnsPaginatedSummaries_whenNoStatusFilter() {
        when(restaurantService.requireRestaurant(5L)).thenReturn(visibleRestaurant());
        Order order = existingOrder(700L, OrderStatus.NEW);
        order.setCustomer(customer("Ali"));
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), Pageable.ofSize(20), 1));

        OwnerOrderListResponse response = orderService.listOrdersForOwner(5L, null, 0, 20);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getCustomerName()).isEqualTo("Ali");
    }

    @Test
    void listOrdersForOwner_filtersByStatus() {
        when(restaurantService.requireRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), eq(OrderStatus.PREPARING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(20), 0));

        orderService.listOrdersForOwner(5L, "preparing", 0, 20);

        verify(orderRepository).findByRestaurantIdAndOptionalStatus(eq(5L), eq(OrderStatus.PREPARING), any(Pageable.class));
    }

    @Test
    void listOrdersForOwner_rejectsConfirmedAndCancelledAsFilterValues() {
        when(restaurantService.requireRestaurant(5L)).thenReturn(visibleRestaurant());

        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, "CONFIRMED", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, "CANCELLED", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForOwner_rejectsUnknownStatusValue() {
        when(restaurantService.requireRestaurant(5L)).thenReturn(visibleRestaurant());

        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, "SHIPPED", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForOwner_rejectsInvalidPagination() {
        when(restaurantService.requireRestaurant(5L)).thenReturn(visibleRestaurant());

        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, null, -1, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, null, 0, 0))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, null, 0, 51))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForOwner_throwsNotFound_whenRestaurantDoesNotExist() {
        when(restaurantService.requireRestaurant(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> orderService.listOrdersForOwner(99L, null, 0, 20))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getOrderForOwner_returnsDetail_whenOwnedByRestaurant() {
        Order order = existingOrder(700L, OrderStatus.NEW);
        order.setCustomer(customer("Ali"));
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        OwnerOrderResponse response = orderService.getOrderForOwner(5L, 700L);

        assertThat(response.getId()).isEqualTo(700L);
        assertThat(response.getCustomerName()).isEqualTo("Ali");
    }

    @Test
    void getOrderForOwner_throwsNotFound_whenOrderBelongsToAnotherRestaurant() {
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderForOwner(5L, 700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getDashboard_returnsStatsAndRecentOrders() {
        Restaurant restaurant = visibleRestaurant();
        when(restaurantService.requireRestaurant(5L)).thenReturn(restaurant);
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.NEW)).thenReturn(3L);
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.PREPARING)).thenReturn(2L);
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.ON_THE_WAY)).thenReturn(1L);
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.DELIVERED)).thenReturn(10L);
        when(orderRepository.countByRestaurantId(5L)).thenReturn(16L);
        Order order = existingOrder(700L, OrderStatus.NEW);
        order.setCustomer(customer("Ali"));
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), Pageable.ofSize(5), 1));

        OwnerDashboardResponse response = orderService.getDashboard(5L);

        assertThat(response.getRestaurantId()).isEqualTo(5L);
        assertThat(response.getStats().getNewCount()).isEqualTo(3L);
        assertThat(response.getStats().getTotalCount()).isEqualTo(16L);
        assertThat(response.getRecentOrders()).hasSize(1);
    }

    @Test
    void getDashboard_throwsNotFound_whenRestaurantDoesNotExist() {
        when(restaurantService.requireRestaurant(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> orderService.getDashboard(99L)).isInstanceOf(RestaurantNotFoundException.class);
    }

    private CheckoutRequest checkoutRequest(Long addressId, String paymentMethod) {
        return checkoutRequest(addressId, paymentMethod, null);
    }

    private CheckoutRequest checkoutRequest(Long addressId, String paymentMethod, String couponCode) {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddressId(addressId);
        request.setPaymentMethod(paymentMethod);
        request.setCouponCode(couponCode);
        return request;
    }

    private Coupon coupon(String code, DiscountType type, BigDecimal value) {
        Coupon coupon = new Coupon();
        coupon.setId(9L);
        coupon.setCode(code);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setActive(true);
        return coupon;
    }

    private Restaurant visibleRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setName("Pizza Place");
        restaurant.setDeliveryFee(BigDecimal.valueOf(12));
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurant.setOpenForOrders(true);
        return restaurant;
    }

    private Cart cartWithOneItem(Restaurant restaurant) {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setRestaurant(restaurant);
        cart.setItems(new ArrayList<>(List.of(cartItem(menuItem(10L, "Pizza", BigDecimal.valueOf(50), true), 2))));
        return cart;
    }

    private CartItem cartItem(MenuItem menuItem, int quantity) {
        CartItem item = new CartItem();
        item.setId(1L);
        item.setMenuItem(menuItem);
        item.setQuantity(quantity);
        return item;
    }

    private MenuItem menuItem(Long id, String name, BigDecimal price, boolean available) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(id);
        menuItem.setName(name);
        menuItem.setPrice(price);
        menuItem.setAvailable(available);
        return menuItem;
    }

    private Address address(Long id) {
        Address address = new Address();
        address.setId(id);
        address.setStreet("Street 1");
        address.setCity("Cairo");
        return address;
    }

    private User customer(String name) {
        User customer = new User();
        customer.setName(name);
        return customer;
    }

    private Order existingOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("ORD-20260825-000001");
        order.setRestaurant(visibleRestaurant());
        order.setDeliveryStreet("Street 1");
        order.setDeliveryCity("Cairo");
        order.setSubtotal(BigDecimal.valueOf(100));
        order.setDeliveryFee(BigDecimal.valueOf(12));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(112));
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }
}
