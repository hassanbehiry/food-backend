package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the customer's order-history table. Deliberately thinner than {@link OrderResponse}
 * — no items, no delivery address — so listing a page of history never needs to fetch each
 * order's item collection; {@code itemCount} is the total quantity across every line, computed
 * separately (see {@code OrderRepository#sumItemQuantitiesByOrderIds}) rather than via a fetch
 * join, to avoid multiplying result rows.
 */
@Getter
@Builder
public class OrderSummaryResponse {

    private Long id;
    private String orderNumber;
    private Long restaurantId;
    private String restaurantName;
    private int itemCount;
    private BigDecimal total;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
