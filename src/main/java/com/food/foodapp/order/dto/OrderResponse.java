package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The persisted order — every field but {@code status} and {@code deliveredAt} is the snapshot
 * recorded at order-creation time. {@code deliveredAt} is {@code null} until the customer confirms
 * receipt via {@code PUT /orders/{id}/confirm-delivery}.
 */
@Getter
@Builder
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private Long restaurantId;
    private String restaurantName;
    private List<OrderItemResponse> items;
    private String deliveryAddress;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
}
