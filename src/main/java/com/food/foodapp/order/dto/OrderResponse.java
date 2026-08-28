package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** The persisted, immutable order — every field is the snapshot recorded at order-creation time. */
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
    private String couponCode;
    private BigDecimal discount;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
