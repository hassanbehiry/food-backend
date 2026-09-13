package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** The four KPI cards atop the owner dashboard's Delivery Orders section. */
@Getter
@Builder
public class DeliveryDashboardSummaryResponse {

    /** Count of orders currently {@code OUT_FOR_DELIVERY}. */
    private long activeCount;
    /** Sum of {@code total} across those same active orders — not yet counted as revenue (see {@code OrderAnalyticsService}). */
    private BigDecimal activeTotalValue;
    /** Orders that reached {@code DELIVERED} today (server-local calendar day). */
    private long deliveredTodayCount;
    /** Revenue recognized today — the same {@code SUM(total) WHERE status = DELIVERED} accounting {@code OrderAnalyticsService} uses, scoped to today. */
    private BigDecimal deliveredTodayRevenue;
}
