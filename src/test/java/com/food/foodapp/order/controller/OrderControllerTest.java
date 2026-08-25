package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.InvalidOrderStatusTransitionException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.order.dto.OrderItemResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.dto.TrackingStepResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void placeOrder_returns201() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(order(700L, OrderStatus.NEW));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":50,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ORD-20260825-000001"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void placeOrder_returns400_whenPaymentMethodMissing() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns409_whenCartEmpty() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new CartEmptyException("Cart is empty"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":50,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrder_returnsOrder() throws Exception {
        when(orderService.getOrder(700L)).thenReturn(order(700L, OrderStatus.NEW));

        mockMvc.perform(get("/api/v1/orders/700"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(700));
    }

    @Test
    void getOrder_returns404_whenMissingOrNotOwned() throws Exception {
        when(orderService.getOrder(999L)).thenThrow(new OrderNotFoundException("Order not found: 999"));

        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void trackOrder_returnsTrackingInfo() throws Exception {
        when(orderService.trackOrder(700L)).thenReturn(OrderTrackingResponse.builder()
                .orderId(700L)
                .orderNumber("ORD-20260825-000001")
                .status(OrderStatus.PREPARING)
                .steps(List.of(
                        TrackingStepResponse.builder().status(OrderStatus.NEW).completed(true).current(false).build(),
                        TrackingStepResponse.builder().status(OrderStatus.PREPARING).completed(true).current(true).build(),
                        TrackingStepResponse.builder().status(OrderStatus.ON_THE_WAY).completed(false).current(false).build(),
                        TrackingStepResponse.builder().status(OrderStatus.DELIVERED).completed(false).current(false).build()))
                .estimatedDeliveryAt(LocalDateTime.of(2026, 8, 25, 13, 0))
                .statusUpdatedAt(LocalDateTime.of(2026, 8, 25, 12, 30))
                .build());

        mockMvc.perform(get("/api/v1/orders/700/track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.steps.length()").value(4))
                .andExpect(jsonPath("$.steps[1].current").value(true));
    }

    @Test
    void trackOrder_returns404_whenMissingOrNotOwned() throws Exception {
        when(orderService.trackOrder(999L)).thenThrow(new OrderNotFoundException("Order not found: 999"));

        mockMvc.perform(get("/api/v1/orders/999/track"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_returnsCancelledOrder() throws Exception {
        when(orderService.cancelOrder(eq(700L))).thenReturn(order(700L, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/orders/700/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_returns409_whenNoLongerCancellable() throws Exception {
        when(orderService.cancelOrder(eq(700L)))
                .thenThrow(new InvalidOrderStatusTransitionException("Order 700 cannot move from PREPARING to CANCELLED"));

        mockMvc.perform(post("/api/v1/orders/700/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_returns404_whenNotOwnedByCaller() throws Exception {
        when(orderService.cancelOrder(eq(700L))).thenThrow(new OrderNotFoundException("Order not found: 700"));

        mockMvc.perform(post("/api/v1/orders/700/cancel"))
                .andExpect(status().isNotFound());
    }

    private OrderResponse order(Long id, OrderStatus status) {
        return OrderResponse.builder()
                .id(id)
                .orderNumber("ORD-20260825-000001")
                .restaurantId(5L)
                .restaurantName("Pizza Place")
                .items(List.of(OrderItemResponse.builder()
                        .id(1L)
                        .menuItemId(10L)
                        .name("Pizza")
                        .img("pizza.jpg")
                        .price(BigDecimal.valueOf(50))
                        .quantity(2)
                        .lineTotal(BigDecimal.valueOf(100))
                        .build()))
                .deliveryAddress("Street 1، Cairo")
                .subtotal(BigDecimal.valueOf(100))
                .deliveryFee(BigDecimal.valueOf(12))
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(112))
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .status(status)
                .build();
    }
}
