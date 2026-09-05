package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the owner dashboard's orders table/tabs. Deliberately thinner than
 * {@link OrderResponse}/{@link OwnerOrderResponse} — no items, no address — so listing a page of
 * orders never needs to fetch each order's item collection.
 */
@Getter
@Builder
public class OwnerOrderSummaryResponse {

    private Long id;
    private String orderNumber;
    private String customerName;
    /** Total item quantity across the order's lines — batch-fetched for the whole list (see {@code OrderRepository.sumItemQuantitiesByOrderIds}), never per row. */
    private int itemCount;
    private BigDecimal total;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
