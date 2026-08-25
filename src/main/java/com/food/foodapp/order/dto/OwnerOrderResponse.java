package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Owner-facing order detail — the same persisted snapshot {@link OrderResponse} exposes to the
 * customer, plus {@code customerName} so the owner knows who to prepare/hand the order to. Kept
 * as its own type rather than adding the field to {@code OrderResponse} so a customer's own
 * {@code GET /orders/{id}} response can never accidentally start echoing their own name back in
 * a place a future refactor might reuse for someone else's order.
 */
@Getter
@Builder
public class OwnerOrderResponse {

    private Long id;
    private String orderNumber;
    private String customerName;
    private List<OrderItemResponse> items;
    private String deliveryAddress;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private String couponCode;
    private BigDecimal discount;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
