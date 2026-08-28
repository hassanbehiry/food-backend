package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The owner dashboard's analytics-overview payload: the "total orders" and "revenue" KPI cards
 * (each with a period-over-period trend percentage), plus forward-looking fields no current
 * screen consumes yet (completed-order count, average order value, orders-by-status) — see
 * {@code OrderAnalyticsService#getOverview} for exactly which order states count toward each
 * field.
 * <p>
 * Always scoped to the current calendar month ({@link #periodStart}/{@link #periodEnd} are
 * exposed so the caller never has to assume "this month" client-side); a {@code null} trend
 * percentage means the previous period had nothing to compare against, not a 0% change.
 */
@Getter
@Builder
public class OwnerAnalyticsOverviewResponse {

    private Long restaurantId;
    private String restaurantName;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private long totalOrders;
    private BigDecimal totalOrdersTrendPercentage;

    private BigDecimal revenue;
    private BigDecimal revenueTrendPercentage;

    private long completedOrders;
    private BigDecimal averageOrderValue;
    private List<OrderStatusCountResponse> ordersByStatus;
}
