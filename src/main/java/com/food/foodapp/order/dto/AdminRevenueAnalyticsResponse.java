package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The admin dashboard's revenue-chart payload for an arbitrary {@code [from, to]} range: one
 * zero-filled {@link DailyRevenueResponse} per calendar day plus a period-over-period
 * {@link #changePercentage} against the immediately preceding range of the same length. Defaults
 * to the current Monday-to-Sunday week when {@code from}/{@code to} are omitted — see
 * {@link AdminOrdersAnalyticsResponse} for why.
 */
@Getter
@Builder
public class AdminRevenueAnalyticsResponse {

    private LocalDate from;
    private LocalDate to;

    private BigDecimal totalRevenue;
    private BigDecimal previousPeriodRevenue;
    private BigDecimal changePercentage;

    private List<DailyRevenueResponse> dailyRevenue;
}
