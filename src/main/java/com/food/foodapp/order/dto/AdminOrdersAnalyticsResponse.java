package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The admin dashboard's orders-chart payload for an arbitrary {@code [from, to]} range: one
 * zero-filled {@link DailyOrderCountResponse} per calendar day plus a period-over-period
 * {@link #changePercentage} against the immediately preceding range of the same length. When
 * {@code from}/{@code to} are omitted, {@code AdminAnalyticsService} defaults the range to the
 * current Monday-to-Sunday week — the canonical week convention the admin dashboard's 7-day
 * orders bar chart uses, distinct from the owner dashboard's Saturday-to-Friday chart (see
 * {@code AdminAnalyticsService#currentWeekMondayToSunday} for why one canonical convention was
 * picked instead of mirroring the owner dashboard's).
 */
@Getter
@Builder
public class AdminOrdersAnalyticsResponse {

    private LocalDate from;
    private LocalDate to;

    private long totalOrders;
    private BigDecimal changePercentage;

    private List<DailyOrderCountResponse> dailyOrders;
}
