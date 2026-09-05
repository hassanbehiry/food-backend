package com.food.foodapp.restaurant.controller;

import com.food.foodapp.restaurant.dto.RestaurantDetailResponse;
import com.food.foodapp.restaurant.dto.RestaurantListResponse;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant discovery HTTP endpoints. Thin controller — all filtering, sorting,
 * pagination and visibility rules live in {@link RestaurantService}.
 */
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    /**
     * GET /api/v1/restaurants
     * Supports {@code q} (name/cuisine search), {@code category} (category <em>slug</em> filter,
     * e.g. {@code ?category=pizza}), {@code sort} (rating | delivery_time | delivery_fee) and
     * pagination.
     */
    @GetMapping
    public ResponseEntity<RestaurantListResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(q, category, sort, page, size));
    }

    /** GET /api/v1/restaurants/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getVisibleRestaurantById(id));
    }
}
