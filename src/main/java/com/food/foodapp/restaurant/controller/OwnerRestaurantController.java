package com.food.foodapp.restaurant.controller;

import com.food.foodapp.restaurant.dto.OwnerRestaurantResponse;
import com.food.foodapp.restaurant.dto.RestaurantSettingsUpdateRequest;
import com.food.foodapp.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-side restaurant settings: load and full settings update (including business hours,
 * which is what drives whether the restaurant shows as open or closed — see
 * {@link com.food.foodapp.restaurant.entity.Restaurant#isCurrentlyOpen()}). Thin controller —
 * all business rules live in {@link RestaurantService}.
 * <p>
 * Authorization: {@code /api/v1/owner/**} requires authentication at the filter chain, and
 * {@link RestaurantService}'s owner methods call
 * {@code RestaurantOwnershipGuard.requireOwnedRestaurant} — a caller who is not this
 * restaurant's owner gets {@code 403}.
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
}
