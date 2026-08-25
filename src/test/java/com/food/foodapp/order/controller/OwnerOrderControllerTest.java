package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.InvalidOrderStatusTransitionException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.order.dto.OrderResponse;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OwnerOrderController.class)
class OwnerOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void updateStatus_returns200_withUpdatedOrder() throws Exception {
        when(orderService.updateOrderStatus(eq(5L), eq(700L), eq("PREPARING")))
                .thenReturn(order(700L, OrderStatus.PREPARING));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    void updateStatus_returns400_whenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_returns400_whenStatusValueUnsupported() throws Exception {
        when(orderService.updateOrderStatus(eq(5L), eq(700L), eq("SHIPPED")))
                .thenThrow(new InvalidRequestParameterException("Invalid 'status' value: 'SHIPPED'"));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_returns409_whenTransitionIllegal() throws Exception {
        when(orderService.updateOrderStatus(eq(5L), eq(700L), eq("DELIVERED")))
                .thenThrow(new InvalidOrderStatusTransitionException("Order 700 cannot move from NEW to DELIVERED"));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_returns404_whenOrderNotOwnedByRestaurant() throws Exception {
        when(orderService.updateOrderStatus(eq(5L), eq(700L), eq("PREPARING")))
                .thenThrow(new OrderNotFoundException("Order not found: 700"));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isNotFound());
    }

    private OrderResponse order(Long id, OrderStatus status) {
        return OrderResponse.builder()
                .id(id)
                .orderNumber("ORD-20260825-000001")
                .restaurantId(5L)
                .restaurantName("Pizza Place")
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
