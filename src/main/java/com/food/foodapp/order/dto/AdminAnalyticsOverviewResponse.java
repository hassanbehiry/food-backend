package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The admin dashboard analytics tab's 4 KPI cards (total orders, total revenue, active
 * restaurants, registered customers — each with a period-over-period trend percentage), plus
 * forward-looking fields no rendered screen consumes yet (average order value, orders-by-status)
 * — see {@code AdminAnalyticsService#getOverview} for exactly which order/restaurant/user states
 * count toward each field.
 * <p>
 * {@link #period} echoes back the resolved preset ({@code 7d}/{@code 30d}/{@code 1y});
 * {@link #periodStart}/{@link #periodEnd} are exposed so the caller never has to recompute the
 * concrete dates a preset resolved to. A {@code null} trend percentage means the previous period
 * had nothing to compare against, not a 0% change.
 */
@Getter
@Builder
public class AdminAnalyticsOverviewResponse {

    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private long totalOrders;
    private BigDecimal totalOrdersTrendPercentage;

    private BigDecimal totalRevenue;
    private BigDecimal totalRevenueTrendPercentage;

    private long activeRestaurants;
    private BigDecimal activeRestaurantsTrendPercentage;

    private long registeredCustomers;
    private BigDecimal registeredCustomersTrendPercentage;

    private BigDecimal averageOrderValue;
    private List<OrderStatusCountResponse> ordersByStatus;
}
