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
import com.food.foodapp.order.dto.DeliveryDashboardResponse;
import com.food.foodapp.order.dto.OrderDeliveryResponse;
import com.food.foodapp.order.dto.OrderListResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.entity.DeliveryConfirmedBy;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.entity.RevenueTransaction;
import com.food.foodapp.order.repository.OrderItemCount;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.order.repository.RevenueTransactionRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import com.food.foodapp.restaurant.service.RestaurantOwnershipGuard;
import com.food.foodapp.common.exception.AccountSuspendedException;
import com.food.foodapp.common.exception.MaintenanceModeException;
import com.food.foodapp.settings.service.PlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserContext userContext;

    @Mock
    private RestaurantOwnershipGuard ownershipGuard;

    @Mock
    private OrderAnalyticsService orderAnalyticsService;

    @Mock
    private PlatformSettingsService platformSettingsService;

    @Mock
    private RevenueTransactionRepository revenueTransactionRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(cartRepository, cartItemRepository, addressRepository, userRepository,
                orderRepository, restaurantRepository, userContext, ownershipGuard, orderAnalyticsService,
                platformSettingsService, revenueTransactionRepository);
        lenient().when(userContext.getCurrentUserId()).thenReturn(1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L)));
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
    void previewCheckout_throwsAccountSuspended_whenCustomerIsSuspended() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        User suspended = activeCustomer(1L);
        suspended.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> orderService.previewCheckout(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(AccountSuspendedException.class);
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
    void previewCheckout_acceptsCashAlias_forCashOnDelivery() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndCustomerId(50L, 1L)).thenReturn(Optional.of(address(50L)));

        CheckoutResponse response = orderService.previewCheckout(checkoutRequest(50L, "cash"));

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
    }

    @Test
    void previewCheckout_usesInlineAddress_withoutTouchingAddressRepository() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        CheckoutResponse response = orderService.previewCheckout(inlineCheckoutRequest("12 Nile St", "Cairo", "cash"));

        assertThat(response.getAddressId()).isNull();
        assertThat(response.getDeliveryAddress()).isEqualTo("12 Nile St، Cairo");
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
        verify(addressRepository, never()).findByIdAndCustomerId(any(), any());
    }

    @Test
    void previewCheckout_throwsInvalidRequestParameter_whenNoDeliveryTargetResolvable() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.previewCheckout(inlineCheckoutRequest(null, null, "cash")))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void placeOrder_snapshotsInlineAddress_andCreatesNoSavedAddressRow() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });

        CheckoutRequest request = inlineCheckoutRequest("12 Nile St", "Cairo", "cash");
        request.setPostalCode("11511");
        request.setLabel("Home");
        request.setNotes("Ring twice");

        OrderResponse response = orderService.placeOrder(request);

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
        assertThat(response.getDeliveryAddress()).isEqualTo("12 Nile St، Cairo، 11511");
        verify(addressRepository, never()).findByIdAndCustomerId(any(), any());
        verify(addressRepository, never()).save(any());
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
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo(BigDecimal.valueOf(100));

        verify(cartItemRepository).deleteByCartId(cart.getId());
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getRestaurant()).isNull();
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
    void placeOrder_throwsAccountSuspended_whenCustomerIsSuspended() {
        Restaurant restaurant = visibleRestaurant();
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        User suspended = activeCustomer(1L);
        suspended.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> orderService.placeOrder(checkoutRequest(50L, "CASH_ON_DELIVERY")))
                .isInstanceOf(AccountSuspendedException.class);
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
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
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
    void cancelOrder_cancels_whenConfirmed() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(700L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_cancels_whenPreparingOrReadyForDelivery_butNotOnceOutForDelivery() {
        Order preparing = existingOrder(700L, OrderStatus.PREPARING);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(preparing));
        assertThat(orderService.cancelOrder(700L).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order ready = existingOrder(701L, OrderStatus.READY_FOR_DELIVERY);
        when(orderRepository.findByIdAndCustomerIdWithItems(701L, 1L)).thenReturn(Optional.of(ready));
        assertThat(orderService.cancelOrder(701L).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order outForDelivery = existingOrder(702L, OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findByIdAndCustomerIdWithItems(702L, 1L)).thenReturn(Optional.of(outForDelivery));
        assertThatThrownBy(() -> orderService.cancelOrder(702L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void cancelOrder_throwsInvalidTransition_onceDelivered() {
        Order order = existingOrder(700L, OrderStatus.DELIVERED);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cancelOrder_throwsNotFound_whenNotOwnedByCaller() {
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void confirmDelivery_marksDelivered_andStampsDeliveredAtAndBy_whenOutForDelivery() {
        Order order = existingOrder(700L, OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findByIdAndCustomerIdForUpdate(700L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.confirmDelivery(700L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.getDeliveredAt()).isNotNull();
        assertThat(order.getDeliveredAt()).isNotNull();
        assertThat(order.getDeliveredBy()).isEqualTo(DeliveryConfirmedBy.CUSTOMER);
        verify(orderRepository).save(order);
        verify(revenueTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmDelivery_throwsNotFound_whenNotOwnedByCaller() {
        when(orderRepository.findByIdAndCustomerIdForUpdate(700L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmDelivery(700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void confirmDelivery_throwsInvalidTransition_whenStillConfirmed() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndCustomerIdForUpdate(700L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmDelivery(700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getDeliveredAt()).isNull();
    }

    @Test
    void confirmDelivery_throwsInvalidTransition_whenAlreadyDelivered() {
        Order order = existingOrder(700L, OrderStatus.DELIVERED);
        when(orderRepository.findByIdAndCustomerIdForUpdate(700L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmDelivery(700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void sendToDelivery_marksOutForDelivery_andStampsTimestampAndDeliveryPerson_whenReadyForDelivery() {
        Order order = existingOrder(700L, OrderStatus.READY_FOR_DELIVERY);
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.sendToDelivery(5L, 700L, "Mohamed");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(order.getSentToDeliveryAt()).isNotNull();
        assertThat(order.getDeliveryPersonName()).isEqualTo("Mohamed");
        verify(orderRepository).save(order);
    }

    @Test
    void sendToDelivery_leavesDeliveryPersonNull_whenNotSupplied() {
        Order order = existingOrder(700L, OrderStatus.READY_FOR_DELIVERY);
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));

        orderService.sendToDelivery(5L, 700L, null);

        assertThat(order.getDeliveryPersonName()).isNull();
    }

    @Test
    void sendToDelivery_throwsInvalidTransition_whenStillConfirmed() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.sendToDelivery(5L, 700L, null))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        assertThat(order.getSentToDeliveryAt()).isNull();
    }

    @Test
    void sendToDelivery_throwsNotFound_whenOrderNotOwnedByRestaurant() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.sendToDelivery(5L, 700L, null))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void sendToDelivery_propagatesAccessDenied_beforeLoadingOrder() {
        when(ownershipGuard.requireOwnedRestaurant(5L))
                .thenThrow(new com.food.foodapp.common.exception.OwnerAccessDeniedException("nope"));

        assertThatThrownBy(() -> orderService.sendToDelivery(5L, 700L, null))
                .isInstanceOf(com.food.foodapp.common.exception.OwnerAccessDeniedException.class);
        verify(orderRepository, never()).findByIdAndRestaurantIdForUpdate(any(), any());
    }

    @Test
    void deliverOrder_marksDelivered_andRecordsRevenue_atomically_whenOutForDelivery() {
        Order order = existingOrder(700L, OrderStatus.OUT_FOR_DELIVERY);
        order.setCustomer(customer("Ali"));
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));
        when(revenueTransactionRepository.saveAndFlush(any(RevenueTransaction.class))).thenAnswer(invocation -> {
            RevenueTransaction tx = invocation.getArgument(0);
            tx.setId(1L);
            tx.setCreatedAt(LocalDateTime.now());
            return tx;
        });

        OrderDeliveryResponse response = orderService.deliverOrder(5L, 700L);

        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredBy()).isEqualTo(DeliveryConfirmedBy.OWNER);
        assertThat(response.getRevenue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(112));
        assertThat(response.getRevenue().getCreatedAt()).isNotNull();

        verify(orderRepository).save(order);
        verify(revenueTransactionRepository).saveAndFlush(argThat(tx ->
                tx.getOrder() == order && tx.getRestaurant() == order.getRestaurant()
                        && tx.getAmount().compareTo(BigDecimal.valueOf(112)) == 0));
    }

    @Test
    void deliverOrder_throwsInvalidTransition_whenNotYetOutForDelivery() {
        Order order = existingOrder(700L, OrderStatus.READY_FOR_DELIVERY);
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(5L, 700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(revenueTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deliverOrder_throwsInvalidTransition_whenAlreadyDelivered_rejectingDuplicateRevenue() {
        Order order = existingOrder(700L, OrderStatus.DELIVERED);
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(5L, 700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(revenueTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deliverOrder_translatesDatabaseUniqueViolation_toInvalidTransition_asDefenseInDepth() {
        Order order = existingOrder(700L, OrderStatus.OUT_FOR_DELIVERY);
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.of(order));
        when(revenueTransactionRepository.saveAndFlush(any(RevenueTransaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> orderService.deliverOrder(5L, 700L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        // The status flip already ran in-memory before the revenue write failed; the surrounding
        // @Transactional is what actually rolls the status change back in production — see
        // OrderService#deliverOrder's javadoc. This unit test cannot exercise real transaction
        // rollback (there is no real datasource here); that is covered at the repository/integration
        // level instead.
    }

    @Test
    void deliverOrder_throwsNotFound_whenOrderNotOwnedByRestaurant() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deliverOrder(5L, 700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void deliverOrder_propagatesAccessDenied_beforeLoadingOrder() {
        when(ownershipGuard.requireOwnedRestaurant(5L))
                .thenThrow(new com.food.foodapp.common.exception.OwnerAccessDeniedException("nope"));

        assertThatThrownBy(() -> orderService.deliverOrder(5L, 700L))
                .isInstanceOf(com.food.foodapp.common.exception.OwnerAccessDeniedException.class);
        verify(orderRepository, never()).findByIdAndRestaurantIdForUpdate(any(), any());
    }

    @Test
    void getDeliveryDashboard_summarizesActiveOrders_andTodaysDeliveredRevenue() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        Order active1 = existingOrder(700L, OrderStatus.OUT_FOR_DELIVERY);
        active1.setCustomer(customer("Ali"));
        active1.getItems().add(new com.food.foodapp.order.entity.OrderItem(
                active1, 10L, "Pizza", null, BigDecimal.valueOf(50), 2, BigDecimal.valueOf(100)));
        Order active2 = existingOrder(701L, OrderStatus.OUT_FOR_DELIVERY);
        active2.setCustomer(customer("Sara"));
        active2.setTotal(BigDecimal.valueOf(200));
        when(orderRepository.findByRestaurantIdAndStatusWithItems(5L, OrderStatus.OUT_FOR_DELIVERY))
                .thenReturn(List.of(active1, active2));
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(
                eq(5L), eq(OrderStatus.DELIVERED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new RevenueAggregate(BigDecimal.valueOf(340), 3L));

        DeliveryDashboardResponse response = orderService.getDeliveryDashboard(5L);

        assertThat(response.getSummary().getActiveCount()).isEqualTo(2);
        assertThat(response.getSummary().getActiveTotalValue()).isEqualByComparingTo(BigDecimal.valueOf(312));
        assertThat(response.getSummary().getDeliveredTodayCount()).isEqualTo(3);
        assertThat(response.getSummary().getDeliveredTodayRevenue()).isEqualByComparingTo(BigDecimal.valueOf(340));
        assertThat(response.getOrders()).hasSize(2);
        assertThat(response.getOrders().get(0).getCustomerName()).isEqualTo("Ali");
        assertThat(response.getOrders().get(0).getItems()).hasSize(1);
    }

    @Test
    void getDeliveryDashboard_throwsAccessDenied_whenCallerDoesNotOwnRestaurant() {
        when(ownershipGuard.requireOwnedRestaurant(5L))
                .thenThrow(new com.food.foodapp.common.exception.OwnerAccessDeniedException("nope"));

        assertThatThrownBy(() -> orderService.getDeliveryDashboard(5L))
                .isInstanceOf(com.food.foodapp.common.exception.OwnerAccessDeniedException.class);
    }

    @Test
    void trackOrder_returnsTrackingResponse_whenOwnedByCaller() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.of(order));

        OrderTrackingResponse response = orderService.trackOrder(700L);

        assertThat(response.getOrderId()).isEqualTo(700L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void trackOrder_throwsNotFound_whenNotOwnedByCaller() {
        when(orderRepository.findByIdAndCustomerIdWithItems(700L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.trackOrder(700L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrdersForCustomer_returnsPaginatedSummaries_withItemCounts() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
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
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
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
    void updateOrderStatus_marksConfirmedOrderAsPreparing() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateOrderStatus(5L, 700L, "PREPARING");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrderStatus_marksPreparingOrderAsReadyForDelivery() {
        Order order = existingOrder(700L, OrderStatus.PREPARING);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateOrderStatus(5L, 700L, "ready_for_delivery");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_FOR_DELIVERY);
    }

    @Test
    void updateOrderStatus_rejectsOutForDeliveryAsExplicitOwnerTarget_dispatchHasItsOwnDedicatedEndpoint() {
        Order order = existingOrder(700L, OrderStatus.READY_FOR_DELIVERY);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "OUT_FOR_DELIVERY"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_rejectsDeliveredAsExplicitOwnerTarget_deliveryHasItsOwnDedicatedEndpoint() {
        Order order = existingOrder(700L, OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "DELIVERED"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_marksConfirmedOrderAsCancelled() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateOrderStatus(5L, 700L, "cancelled");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateOrderStatus_rejectsIllegalTransition_onceOutForDelivery() {
        Order order = existingOrder(700L, OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "CANCELLED"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void updateOrderStatus_rejectsUnknownStatusValue() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "SHIPPED"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_rejectsConfirmedAsExplicitOwnerTarget() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "CONFIRMED"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateOrderStatus_throwsNotFound_whenOrderNotOwnedByRestaurant() {
        when(orderRepository.findByIdAndRestaurantIdWithItems(700L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, 700L, "DELIVERED"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrdersForOwner_returnsPaginatedSummaries_whenNoStatusFilter() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
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
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), eq(OrderStatus.CONFIRMED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(20), 0));

        orderService.listOrdersForOwner(5L, "confirmed", 0, 20);

        verify(orderRepository).findByRestaurantIdAndOptionalStatus(eq(5L), eq(OrderStatus.CONFIRMED), any(Pageable.class));
    }

    @Test
    void listOrdersForOwner_filtersByCancelled() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), eq(OrderStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(20), 0));

        orderService.listOrdersForOwner(5L, "CANCELLED", 0, 20);

        verify(orderRepository).findByRestaurantIdAndOptionalStatus(eq(5L), eq(OrderStatus.CANCELLED), any(Pageable.class));
    }

    @Test
    void listOrdersForOwner_rejectsUnknownStatusValue() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());

        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, "SHIPPED", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForOwner_rejectsInvalidPagination() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(visibleRestaurant());

        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, null, -1, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, null, 0, 0))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> orderService.listOrdersForOwner(5L, null, 0, 51))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listOrdersForOwner_throwsNotFound_whenRestaurantDoesNotExist() {
        when(ownershipGuard.requireOwnedRestaurant(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> orderService.listOrdersForOwner(99L, null, 0, 20))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getOrderForOwner_returnsDetail_whenOwnedByRestaurant() {
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
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
    void getDashboard_returnsStatsRecentOrdersWithItemCountsAndDelegatedAnalytics() {
        Restaurant restaurant = visibleRestaurant();
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant);
        stubDashboardCounts();
        Order order = existingOrder(700L, OrderStatus.CONFIRMED);
        order.setCustomer(customer("Ali"));
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), Pageable.ofSize(5), 1));
        when(orderRepository.sumItemQuantitiesByOrderIds(List.of(700L)))
                .thenReturn(List.of(new OrderItemCount(700L, 4L)));
        when(orderAnalyticsService.getOverview(5L)).thenReturn(overview(42L, BigDecimal.valueOf(3200)));
        when(orderAnalyticsService.getRevenue(5L, null, null)).thenReturn(sevenDayRevenue(BigDecimal.valueOf(8)));

        OwnerDashboardResponse response = orderService.getDashboard(5L);

        assertThat(response.getRestaurantId()).isEqualTo(5L);
        assertThat(response.getStats().getConfirmedCount()).isEqualTo(3L);
        assertThat(response.getStats().getTotalCount()).isEqualTo(16L);
        assertThat(response.getRecentOrders()).hasSize(1);
        assertThat(response.getRecentOrders().get(0).getItemCount()).isEqualTo(4);
        assertThat(response.getMonthOrders()).isEqualTo(42L);
        assertThat(response.getMonthRevenue()).isEqualByComparingTo(BigDecimal.valueOf(3200));
        assertThat(response.getLast7DaysRevenue()).hasSize(7);
        assertThat(response.getWeekOverWeekPct()).isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    @Test
    void getDashboard_throwsNotFound_whenRestaurantDoesNotExist() {
        when(ownershipGuard.requireOwnedRestaurant(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> orderService.getDashboard(99L)).isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getDashboard_noArg_resolvesCallersOwnRestaurant_andBuildsSamePayload() {
        Restaurant restaurant = visibleRestaurant();
        when(restaurantRepository.findByOwnerId(1L)).thenReturn(Optional.of(restaurant));
        stubDashboardCounts();
        when(orderRepository.findByRestaurantIdAndOptionalStatus(eq(5L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(5), 0));
        when(orderAnalyticsService.getOverview(5L)).thenReturn(overview(0L, BigDecimal.ZERO));
        when(orderAnalyticsService.getRevenue(5L, null, null)).thenReturn(sevenDayRevenue(BigDecimal.ZERO));

        OwnerDashboardResponse response = orderService.getDashboard();

        assertThat(response.getRestaurantId()).isEqualTo(5L);
        assertThat(response.getRecentOrders()).isEmpty();
        assertThat(response.getMonthOrders()).isZero();
        assertThat(response.getLast7DaysRevenue()).hasSize(7);
        verify(ownershipGuard, never()).requireOwnedRestaurant(any());
    }

    @Test
    void getDashboard_noArg_throwsRestaurantNotFound_whenCallerOwnsNoRestaurant() {
        when(restaurantRepository.findByOwnerId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getDashboard()).isInstanceOf(RestaurantNotFoundException.class);
    }

    private void stubDashboardCounts() {
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.CONFIRMED)).thenReturn(3L);
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.DELIVERED)).thenReturn(10L);
        when(orderRepository.countByRestaurantIdAndStatus(5L, OrderStatus.CANCELLED)).thenReturn(3L);
        when(orderRepository.countByRestaurantId(5L)).thenReturn(16L);
    }

    private OwnerAnalyticsOverviewResponse overview(long totalOrders, BigDecimal revenue) {
        return OwnerAnalyticsOverviewResponse.builder()
                .restaurantId(5L).restaurantName("Pizza Place")
                .totalOrders(totalOrders).totalOrdersTrendPercentage(BigDecimal.valueOf(12.5))
                .revenue(revenue).revenueTrendPercentage(BigDecimal.valueOf(8))
                .build();
    }

    private OwnerRevenueAnalyticsResponse sevenDayRevenue(BigDecimal changePct) {
        List<com.food.foodapp.order.dto.DailyRevenueResponse> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            points.add(com.food.foodapp.order.dto.DailyRevenueResponse.builder()
                    .date(LocalDate.of(2026, 8, 22).plusDays(i)).revenue(BigDecimal.ZERO).orderCount(0).build());
        }
        return OwnerRevenueAnalyticsResponse.builder()
                .restaurantId(5L).changePercentage(changePct).dailyRevenue(points).build();
    }

    private CheckoutRequest checkoutRequest(Long addressId, String paymentMethod) {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddressId(addressId);
        request.setPaymentMethod(paymentMethod);
        return request;
    }

    private CheckoutRequest inlineCheckoutRequest(String street, String city, String paymentMethod) {
        CheckoutRequest request = new CheckoutRequest();
        request.setStreet(street);
        request.setCity(city);
        request.setPaymentMethod(paymentMethod);
        return request;
    }

    private Restaurant visibleRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setName("Pizza Place");
        restaurant.setDeliveryFee(BigDecimal.valueOf(12));
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
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

    private User activeCustomer(Long id) {
        User customer = new User();
        customer.setId(id);
        customer.setStatus(UserStatus.ACTIVE);
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
        order.setTotal(BigDecimal.valueOf(112));
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }
}
