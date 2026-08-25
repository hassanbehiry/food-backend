package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Per-status-tab counts backing the owner dashboard's badge numbers. {@code CONFIRMED} has no
 * count of its own — it's an internal, effectively-instantaneous transition an order never rests
 * in (see {@code OrderStatus}) — and {@code CANCELLED} isn't one of the dashboard's tabs either;
 * both are still reflected in {@code totalCount}.
 */
@Getter
@Builder
public class OwnerOrderStatsResponse {

    private long newCount;
    private long preparingCount;
    private long onTheWayCount;
    private long deliveredCount;
    private long totalCount;
}
