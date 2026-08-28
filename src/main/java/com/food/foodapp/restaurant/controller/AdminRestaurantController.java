package com.food.foodapp.restaurant.controller;

import com.food.foodapp.restaurant.dto.AdminRestaurantListResponse;
import com.food.foodapp.restaurant.dto.AdminRestaurantResponse;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin restaurant approval and status management: paginated/filterable listing,
 * detail, and the approve/reject/suspend actions. Thin controller — all business rules and
 * legal-transition checks live in {@link RestaurantService}.
 * <p>
 * There is no separate "restore" endpoint: {@code /approve} is also how an admin reactivates a
 * {@code SUSPENDED} restaurant, since both cases mean "this restaurant is active" — see
 * {@code RestaurantApprovalStatus} for the transition table.
 * <p>
 * NOTE: same authorization gap as {@link com.food.foodapp.menu.controller.OwnerMenuCategoryController}
 * — this codebase has no admin-authentication middleware yet (no {@code ADMIN} role, no Spring
 * Security), so these endpoints are not yet gated to an authenticated admin.
 */
@RestController
@RequestMapping("/api/v1/admin/restaurants")
@RequiredArgsConstructor
public class AdminRestaurantController {

    private final RestaurantService restaurantService;

    /**
     * GET /api/v1/admin/restaurants
     * Supports {@code status} (pending | approved | rejected | suspended) and pagination.
     */
    @GetMapping
    public ResponseEntity<AdminRestaurantListResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(restaurantService.listRestaurantsForAdmin(status, page, size));
    }

    /** GET /api/v1/admin/restaurants/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<AdminRestaurantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getAdminRestaurant(id));
    }

    /** PATCH /api/v1/admin/restaurants/{id}/approve — also reactivates a suspended restaurant. */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<AdminRestaurantResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.approveRestaurant(id));
    }

    /** PATCH /api/v1/admin/restaurants/{id}/reject */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<AdminRestaurantResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.rejectRestaurant(id));
    }

    /** PATCH /api/v1/admin/restaurants/{id}/suspend */
    @PatchMapping("/{id}/suspend")
    public ResponseEntity<AdminRestaurantResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.suspendRestaurant(id));
    }
}
