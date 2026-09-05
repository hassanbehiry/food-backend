package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Combined landing-view payload for the owner dashboard, served by both
 * {@code GET /owner/dashboard/{restaurantId}} and the no-argument {@code GET /owner/dashboard}
 * (which resolves the caller's own restaurant). One round trip carries everything
 * {@code OwnerDashboardPage} renders on its overview tab:
 * <ul>
 *   <li>the status-tab counts ({@link #stats}) and a short recent-orders list
 *       ({@link #recentOrders}, each row carrying its {@code itemCount}), so the initial render
 *       doesn't need a separate call to the paginated orders endpoint just for its first page;</li>
 *   <li>the two month-to-date KPI cards — {@link #monthOrders}/{@link #monthOrdersTrendPct} and
 *       {@link #monthRevenue}/{@link #monthRevenueTrendPct} — and the last-7-days revenue chart
 *       ({@link #last7DaysRevenue} + {@link #weekOverWeekPct}), all delegated to
 *       {@code OrderAnalyticsService} (the same figures its {@code /analytics/overview} and
 *       {@code /analytics/revenue} endpoints expose), so the dashboard needn't fan out to those
 *       two endpoints itself.</li>
 * </ul>
 * A {@code null} trend percentage means the comparison period had nothing to compare against
 * (show "new"/"—" rather than a percentage), not a 0% change. On a brand-new restaurant with no
 * orders the KPI numbers are {@code 0} and {@link #last7DaysRevenue} is seven zero-valued points.
 */
@Getter
@Builder
public class OwnerDashboardResponse {

    private Long restaurantId;
    private String restaurantName;
    private OwnerOrderStatsResponse stats;
    private List<OwnerOrderSummaryResponse> recentOrders;

    /** Orders placed at this restaurant since the 1st of the current calendar month, every status counted (order volume). */
    private long monthOrders;
    /** Month-to-date order volume vs. the equivalent day-span of the previous month; {@code null} if last month had none. */
    private BigDecimal monthOrdersTrendPct;

    /** Delivered-order revenue since the 1st of the current calendar month. */
    private BigDecimal monthRevenue;
    /** Month-to-date revenue vs. the equivalent day-span of the previous month; {@code null} if last month had none. */
    private BigDecimal monthRevenueTrendPct;

    /** One point per day for the current Saturday-to-Friday week (seven entries, zero-filled) — matches {@code /analytics/revenue}'s {@code dailyRevenue}. */
    private List<DailyRevenueResponse> last7DaysRevenue;
    /** This week's delivered revenue vs. the previous week's; {@code null} if the previous week had none. */
    private BigDecimal weekOverWeekPct;
}
