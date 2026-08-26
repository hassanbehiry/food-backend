package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The owner dashboard's revenue-chart payload for an arbitrary {@code [from, to]} range: one
 * zero-filled {@link DailyRevenueResponse} per calendar day plus a period-over-period
 * {@link #changePercentage} against the immediately preceding range of the same length. When
 * {@code from}/{@code to} are omitted, {@code OrderAnalyticsService} defaults the range to the
 * current Saturday-to-Friday week, which makes this badge a week-over-week change — the
 * dashboard's default chart request never needs to compute that boundary itself.
 */
@Getter
@Builder
public class OwnerRevenueAnalyticsResponse {

    private Long restaurantId;
    private LocalDate from;
    private LocalDate to;

    private BigDecimal totalRevenue;
    private BigDecimal previousPeriodRevenue;
    private BigDecimal changePercentage;

    private List<DailyRevenueResponse> dailyRevenue;
}
