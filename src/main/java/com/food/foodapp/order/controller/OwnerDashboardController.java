package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner dashboard's combined landing-view overview: status-tab counts and a short
 * recent-orders list in one round trip, layered on top of {@link OwnerOrderController}'s
 * paginated/filterable order list rather than replacing it — the dashboard's "Orders" tab still
 * calls that endpoint directly for full pagination and status filtering.
 * <p>
 * Only the {@code {restaurantId}}-scoped form is implemented: resolving "the current owner's
 * restaurant" without a path id isn't possible yet, since no owner-authentication/owner-restaurant
 * link exists in this codebase (same gap {@link OwnerOrderController} already documents).
 */
@RestController
@RequestMapping("/api/v1/owner/dashboard")
@RequiredArgsConstructor
public class OwnerDashboardController {

    private final OrderService orderService;

    /** GET /api/v1/owner/dashboard/{restaurantId} */
    @GetMapping("/{restaurantId}")
    public ResponseEntity<OwnerDashboardResponse> getDashboard(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(orderService.getDashboard(restaurantId));
    }
}
