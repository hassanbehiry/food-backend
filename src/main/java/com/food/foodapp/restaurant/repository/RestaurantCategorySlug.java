package com.food.foodapp.restaurant.repository;

/**
 * One (restaurant id, category slug) pair — the row shape of
 * {@link RestaurantRepository#findCategorySlugsByRestaurantIds}. Loaded as a single separate
 * query for a whole page of restaurants (rather than a fetch join or per-row lazy access) so
 * the restaurant list's {@code categoryIds} field never triggers an N+1 and the to-many join
 * can't multiply / break pagination.
 */
public record RestaurantCategorySlug(Long restaurantId, String slug) {
}
