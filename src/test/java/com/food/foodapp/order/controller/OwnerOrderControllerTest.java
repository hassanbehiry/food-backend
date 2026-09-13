package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.InvalidOrderStatusTransitionException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.common.exception.OwnerAccessDeniedException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.order.dto.DeliveryDashboardResponse;
import com.food.foodapp.order.dto.DeliveryDashboardSummaryResponse;
import com.food.foodapp.order.dto.OrderDeliveryResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerOrderSummaryResponse;
import com.food.foodapp.order.dto.RevenueResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OwnerOrderController.class)
class OwnerOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void ownerEndpoint_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(orderService.listOrdersForOwner(eq(2L), isNull(), eq(0), eq(20)))
                .thenThrow(new OwnerAccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/owner/restaurants/2/orders"))
                .andExpect(status().isForbidden());
    }


    @Test
    void updateStatus_returns200_withUpdatedOrder() throws Exception {
        when(orderService.updateOrderStatus(eq(5L), eq(700L), eq("DELIVERED")))
                .thenReturn(order(700L, OrderStatus.DELIVERED));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
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
                .thenThrow(new InvalidOrderStatusTransitionException("Order 700 cannot move from CANCELLED to DELIVERED"));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_returns404_whenOrderNotOwnedByRestaurant() throws Exception {
        when(orderService.updateOrderStatus(eq(5L), eq(700L), eq("DELIVERED")))
                .thenThrow(new OrderNotFoundException("Order not found: 700"));

        mockMvc.perform(patch("/api/v1/owner/restaurants/5/orders/700/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returns200_withPagedOrders() throws Exception {
        OwnerOrderListResponse response = OwnerOrderListResponse.builder()
                .orders(List.of(summary(700L, OrderStatus.CONFIRMED)))
                .page(0).size(20).totalElements(1).totalPages(1)
                .build();
        when(orderService.listOrdersForOwner(eq(5L), isNull(), eq(0), eq(20))).thenReturn(response);

        mockMvc.perform(get("/api/v1/owner/restaurants/5/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].id").value(700))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_passesStatusFilterThrough() throws Exception {
        when(orderService.listOrdersForOwner(eq(5L), eq("confirmed"), eq(0), eq(20)))
                .thenReturn(OwnerOrderListResponse.builder()
                        .orders(List.of()).page(0).size(20).totalElements(0).totalPages(0).build());

        mockMvc.perform(get("/api/v1/owner/restaurants/5/orders").param("status", "confirmed"))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns400_whenStatusValueUnsupported() throws Exception {
        when(orderService.listOrdersForOwner(eq(5L), eq("SHIPPED"), eq(0), eq(20)))
                .thenThrow(new InvalidRequestParameterException("Invalid 'status' value: 'SHIPPED'"));

        mockMvc.perform(get("/api/v1/owner/restaurants/5/orders").param("status", "SHIPPED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returns404_whenRestaurantDoesNotExist() throws Exception {
        when(orderService.listOrdersForOwner(eq(99L), isNull(), eq(0), eq(20)))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/owner/restaurants/99/orders"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns200_withOrderDetail() throws Exception {
        OwnerOrderResponse response = OwnerOrderResponse.builder()
                .id(700L).orderNumber("ORD-20260825-000001").customerName("Ali")
                .status(OrderStatus.CONFIRMED).total(BigDecimal.valueOf(112))
                .build();
        when(orderService.getOrderForOwner(5L, 700L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/owner/restaurants/5/orders/700"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ali"));
    }

    @Test
    void getById_returns404_whenOrderNotOwnedByRestaurant() throws Exception {
        when(orderService.getOrderForOwner(5L, 700L)).thenThrow(new OrderNotFoundException("Order not found: 700"));

        mockMvc.perform(get("/api/v1/owner/restaurants/5/orders/700"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendToDelivery_returns200_withUpdatedOrder() throws Exception {
        when(orderService.sendToDelivery(eq(5L), eq(700L), eq("Mohamed")))
                .thenReturn(order(700L, OrderStatus.OUT_FOR_DELIVERY));

        mockMvc.perform(post("/api/v1/owner/restaurants/5/orders/700/send-to-delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryPersonName\":\"Mohamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void sendToDelivery_acceptsMissingBody() throws Exception {
        when(orderService.sendToDelivery(eq(5L), eq(700L), isNull()))
                .thenReturn(order(700L, OrderStatus.OUT_FOR_DELIVERY));

        mockMvc.perform(post("/api/v1/owner/restaurants/5/orders/700/send-to-delivery"))
                .andExpect(status().isOk());
    }

    @Test
    void sendToDelivery_returns409_whenNotReadyForDelivery() throws Exception {
        when(orderService.sendToDelivery(eq(5L), eq(700L), isNull()))
                .thenThrow(new InvalidOrderStatusTransitionException("Order 700 cannot move from CONFIRMED to OUT_FOR_DELIVERY"));

        mockMvc.perform(post("/api/v1/owner/restaurants/5/orders/700/send-to-delivery"))
                .andExpect(status().isConflict());
    }

    @Test
    void sendToDelivery_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(orderService.sendToDelivery(eq(2L), eq(700L), isNull()))
                .thenThrow(new OwnerAccessDeniedException("nope"));

        mockMvc.perform(post("/api/v1/owner/restaurants/2/orders/700/send-to-delivery"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deliver_returns200_withOrderAndRevenue() throws Exception {
        OrderDeliveryResponse response = OrderDeliveryResponse.builder()
                .order(OwnerOrderResponse.builder().id(700L).status(OrderStatus.DELIVERED)
                        .total(BigDecimal.valueOf(112)).build())
                .revenue(RevenueResponse.builder().amount(BigDecimal.valueOf(112))
                        .createdAt(LocalDateTime.of(2026, 9, 12, 23, 36, 41)).build())
                .build();
        when(orderService.deliverOrder(5L, 700L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/owner/restaurants/5/orders/700/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("DELIVERED"))
                .andExpect(jsonPath("$.revenue.amount").value(112));
    }

    @Test
    void deliver_returns409_whenNotOutForDelivery() throws Exception {
        when(orderService.deliverOrder(5L, 700L))
                .thenThrow(new InvalidOrderStatusTransitionException("Order 700 has already been delivered"));

        mockMvc.perform(post("/api/v1/owner/restaurants/5/orders/700/deliver"))
                .andExpect(status().isConflict());
    }

    @Test
    void deliver_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(orderService.deliverOrder(2L, 700L)).thenThrow(new OwnerAccessDeniedException("nope"));

        mockMvc.perform(post("/api/v1/owner/restaurants/2/orders/700/deliver"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deliver_returns404_whenOrderNotOwnedByRestaurant() throws Exception {
        when(orderService.deliverOrder(5L, 700L)).thenThrow(new OrderNotFoundException("Order not found: 700"));

        mockMvc.perform(post("/api/v1/owner/restaurants/5/orders/700/deliver"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deliveryDashboard_returns200_withSummaryAndOrders() throws Exception {
        DeliveryDashboardResponse response = DeliveryDashboardResponse.builder()
                .summary(DeliveryDashboardSummaryResponse.builder()
                        .activeCount(2).activeTotalValue(BigDecimal.valueOf(300))
                        .deliveredTodayCount(5).deliveredTodayRevenue(BigDecimal.valueOf(600))
                        .build())
                .orders(List.of())
                .build();
        when(orderService.getDeliveryDashboard(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/owner/restaurants/5/orders/delivery-dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.activeCount").value(2))
                .andExpect(jsonPath("$.summary.deliveredTodayRevenue").value(600));
    }

    @Test
    void deliveryDashboard_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(orderService.getDeliveryDashboard(2L)).thenThrow(new OwnerAccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/owner/restaurants/2/orders/delivery-dashboard"))
                .andExpect(status().isForbidden());
    }

    private OwnerOrderSummaryResponse summary(Long id, OrderStatus status) {
        return OwnerOrderSummaryResponse.builder()
                .id(id).orderNumber("ORD-20260825-000001").customerName("Ali")
                .total(BigDecimal.valueOf(112)).status(status)
                .build();
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
                .total(BigDecimal.valueOf(112))
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .status(status)
                .build();
    }
}
