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
 * The owner dashboard's combined landing-view overview: status-tab counts, a short recent-orders
 * list, the month-to-date KPI cards and the last-7-days revenue series in one round trip, layered
 * on top of {@link OwnerOrderController}'s paginated/filterable order list and
 * {@code OwnerAnalyticsController}'s finer-grained analytics rather than replacing them — the
 * dashboard's "Orders" tab still calls the orders endpoint directly for full pagination and
 * status filtering.
 * <p>
 * Two forms:
 * <ul>
 *   <li>{@code GET /api/v1/owner/dashboard} — no path variable; resolves the caller's own
 *       restaurant. {@code 404} if the caller owns no restaurant.</li>
 *   <li>{@code GET /api/v1/owner/dashboard/{restaurantId}} — explicit id, authorized by
 *       {@code RestaurantOwnershipGuard.requireOwnedRestaurant} in {@code OrderService}, so a
 *       caller who does not own {@code restaurantId} gets {@code 403}.</li>
 * </ul>
 * An anonymous caller gets {@code 401} at the filter chain either way ({@code /owner/**} is
 * authenticated).
 */
@RestController
@RequestMapping("/api/v1/owner/dashboard")
@RequiredArgsConstructor
public class OwnerDashboardController {

    private final OrderService orderService;

    /** GET /api/v1/owner/dashboard — the caller's own restaurant, resolved from the authenticated owner. */
    @GetMapping
    public ResponseEntity<OwnerDashboardResponse> getDashboard() {
        return ResponseEntity.ok(orderService.getDashboard());
    }

    /** GET /api/v1/owner/dashboard/{restaurantId} */
    @GetMapping("/{restaurantId}")
    public ResponseEntity<OwnerDashboardResponse> getDashboard(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(orderService.getDashboard(restaurantId));
    }
}
