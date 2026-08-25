package com.food.foodapp.order.mapper;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    @Test
    void toCheckoutResponse_composesDeliveryAddressAndEchoesComputedAmounts() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setName("Pizza Place");

        Address address = new Address();
        address.setId(50L);
        address.setStreet("Street 1");
        address.setCity("Cairo");

        CartItem item = new CartItem();
        item.setId(1L);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(10L);
        menuItem.setName("Pizza");
        menuItem.setPrice(BigDecimal.valueOf(50));
        item.setMenuItem(menuItem);
        item.setQuantity(2);

        CheckoutResponse response = OrderMapper.toCheckoutResponse(restaurant, List.of(item), address,
                PaymentMethod.CASH_ON_DELIVERY, BigDecimal.valueOf(100), BigDecimal.valueOf(12), BigDecimal.ZERO,
                BigDecimal.valueOf(112));

        assertThat(response.getRestaurantId()).isEqualTo(5L);
        assertThat(response.getAddressId()).isEqualTo(50L);
        assertThat(response.getDeliveryAddress()).isEqualTo("Street 1، Cairo");
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(112));
    }

    @Test
    void toResponse_composesDeliveryAddress_fromSnapshottedOrderFields() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setName("Pizza Place");

        Order order = new Order();
        order.setId(700L);
        order.setOrderNumber("ORD-20260825-000001");
        order.setRestaurant(restaurant);
        order.setDeliveryStreet("Street 1");
        order.setDeliveryCity("Cairo");
        order.setSubtotal(BigDecimal.valueOf(100));
        order.setDeliveryFee(BigDecimal.valueOf(12));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(112));
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setStatus(OrderStatus.NEW);

        OrderItem item = new OrderItem(order, 10L, "Pizza", "pizza.jpg", BigDecimal.valueOf(50), 2,
                BigDecimal.valueOf(100));
        item.setId(1L);
        order.setItems(List.of(item));

        OrderResponse response = OrderMapper.toResponse(order);

        assertThat(response.getOrderNumber()).isEqualTo("ORD-20260825-000001");
        assertThat(response.getDeliveryAddress()).isEqualTo("Street 1، Cairo");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getName()).isEqualTo("Pizza");
        assertThat(response.getItems().get(0).getImg()).isEqualTo("pizza.jpg");
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void toTracking_newOrder_showsFirstStepAsCurrent_andComputesEta() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setEstimatedDeliveryMaxMinutes(45);

        Order order = new Order();
        order.setId(700L);
        order.setOrderNumber("ORD-20260825-000001");
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(LocalDateTime.of(2026, 8, 25, 12, 0));
        order.setUpdatedAt(LocalDateTime.of(2026, 8, 25, 12, 0));

        OrderTrackingResponse response = OrderMapper.toTracking(order);

        assertThat(response.getOrderId()).isEqualTo(700L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getEstimatedDeliveryAt()).isEqualTo(LocalDateTime.of(2026, 8, 25, 12, 45));
        assertThat(response.getSteps()).hasSize(4);
        assertThat(response.getSteps().get(0).getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getSteps().get(0).isCurrent()).isTrue();
        assertThat(response.getSteps().get(0).isCompleted()).isTrue();
        assertThat(response.getSteps().get(1).isCompleted()).isFalse();
        assertThat(response.getSteps().get(1).isCurrent()).isFalse();
    }

    @Test
    void toTracking_confirmedOrder_isRenderedAsNewStepStillCurrent() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setEstimatedDeliveryMaxMinutes(45);

        Order order = new Order();
        order.setId(700L);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCreatedAt(LocalDateTime.of(2026, 8, 25, 12, 0));

        OrderTrackingResponse response = OrderMapper.toTracking(order);

        assertThat(response.getSteps().get(0).getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getSteps().get(0).isCurrent()).isTrue();
        assertThat(response.getSteps().get(1).isCurrent()).isFalse();
    }

    @Test
    void toTracking_deliveredOrder_marksAllStepsCompleted_andClearsEta() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setEstimatedDeliveryMaxMinutes(45);

        Order order = new Order();
        order.setId(700L);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.of(2026, 8, 25, 12, 0));

        OrderTrackingResponse response = OrderMapper.toTracking(order);

        assertThat(response.getEstimatedDeliveryAt()).isNull();
        assertThat(response.getSteps()).allSatisfy(step -> assertThat(step.isCompleted()).isTrue());
        assertThat(response.getSteps().get(3).isCurrent()).isTrue();
    }

    @Test
    void toTracking_cancelledOrder_marksNoStepCompletedOrCurrent_andClearsEta() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setEstimatedDeliveryMaxMinutes(45);

        Order order = new Order();
        order.setId(700L);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCreatedAt(LocalDateTime.of(2026, 8, 25, 12, 0));

        OrderTrackingResponse response = OrderMapper.toTracking(order);

        assertThat(response.getEstimatedDeliveryAt()).isNull();
        assertThat(response.getSteps()).allSatisfy(step -> {
            assertThat(step.isCompleted()).isFalse();
            assertThat(step.isCurrent()).isFalse();
        });
    }
}
