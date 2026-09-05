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
 * Only the {@code {restaurantId}}-scoped form is implemented here; the no-argument
 * {@code GET /api/v1/owner/dashboard} that resolves the caller's own restaurant via
 * {@code RestaurantRepository.findByOwnerId} is a later task (BACKEND-008). Authorization:
 * {@code OrderService.getDashboard} calls {@code RestaurantOwnershipGuard.requireOwnedRestaurant},
 * so a caller who does not own {@code restaurantId} gets {@code 403} (and an anonymous caller
 * {@code 401} at the filter chain).
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
