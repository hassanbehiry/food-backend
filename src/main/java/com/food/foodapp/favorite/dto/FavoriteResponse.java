package com.food.foodapp.favorite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.food.foodapp.restaurant.dto.RestaurantSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One favorited restaurant. {@code restaurant} is always the restaurant's current,
 * customer-facing data (never a snapshot from favoriting time) — prices, rating, and
 * open/closed state are as fresh as {@link com.food.foodapp.restaurant.mapper.RestaurantMapper}
 * produces for restaurant discovery itself. {@code isAvailable} is the one field this
 * endpoint adds on top of that: whether the restaurant currently passes
 * {@code RestaurantService#isCustomerVisible} (admin-approved and open for orders). A
 * favorite row is never deleted just because a restaurant becomes unavailable — this
 * flag lets the client show it as greyed-out/unorderable instead of silently dropping it.
 */
@Getter
@Builder
public class FavoriteResponse {

    private RestaurantSummaryResponse restaurant;

    @JsonProperty("isAvailable")
    private boolean available;

    private LocalDateTime favoritedAt;
}
