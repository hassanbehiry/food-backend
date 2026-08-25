package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Combined landing-view payload for {@code GET /owner/dashboard/{restaurantId}}: status-tab
 * counts and a short recent-orders list in one round trip, so the dashboard's initial render
 * doesn't need a separate call to the paginated orders endpoint just to show its first page.
 */
@Getter
@Builder
public class OwnerDashboardResponse {

    private Long restaurantId;
    private String restaurantName;
    private OwnerOrderStatsResponse stats;
    private List<OwnerOrderSummaryResponse> recentOrders;
}
