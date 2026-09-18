package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** {@code GET /owner/restaurants/{restaurantId}/orders/delivery-dashboard} — see {@code OrderService#getDeliveryDashboard}. */
@Getter
@Builder
public class DeliveryDashboardResponse {

    private DeliveryDashboardSummaryResponse summary;
    private List<DeliveryOrderResponse> orders;
}
