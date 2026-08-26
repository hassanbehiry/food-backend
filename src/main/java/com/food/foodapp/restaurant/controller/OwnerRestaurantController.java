package com.food.foodapp.restaurant.controller;

import com.food.foodapp.restaurant.dto.OwnerRestaurantResponse;
import com.food.foodapp.restaurant.dto.RestaurantAvailabilityRequest;
import com.food.foodapp.restaurant.dto.RestaurantSettingsUpdateRequest;
import com.food.foodapp.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-side restaurant settings: load, full settings update, and the
 * pause/resume storefront toggle. Thin controller — all business rules live in
 * {@link RestaurantService}.
 * <p>
 * NOTE: same authorization gap as {@link com.food.foodapp.menu.controller.OwnerMenuCategoryController}
 * — this codebase has no owner-authentication middleware yet, so these endpoints are
 * only scoped to "the restaurant exists", not to the authenticated owner.
 */
@RestController
@RequestMapping("/api/v1/owner/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class OwnerRestaurantController {

    private final RestaurantService restaurantService;

    /** GET /api/v1/owner/restaurants/{restaurantId} */
    @GetMapping
    public ResponseEntity<OwnerRestaurantResponse> get(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(restaurantService.getOwnerRestaurant(restaurantId));
    }

    /** PUT /api/v1/owner/restaurants/{restaurantId}/settings */
    @PutMapping("/settings")
    public ResponseEntity<OwnerRestaurantResponse> updateSettings(
            @PathVariable Long restaurantId, @Valid @RequestBody RestaurantSettingsUpdateRequest request) {
        return ResponseEntity.ok(restaurantService.updateSettings(restaurantId, request));
    }

    /** PATCH /api/v1/owner/restaurants/{restaurantId}/availability */
    @PatchMapping("/availability")
    public ResponseEntity<OwnerRestaurantResponse> updateAvailability(
            @PathVariable Long restaurantId, @Valid @RequestBody RestaurantAvailabilityRequest request) {
        return ResponseEntity.ok(restaurantService.updateAvailability(restaurantId, request));
    }
}
