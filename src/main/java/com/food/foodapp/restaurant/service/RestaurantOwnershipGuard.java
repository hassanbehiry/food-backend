package com.food.foodapp.restaurant.service;

import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.OwnerAccessDeniedException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single authorization gate for every {@code /api/v1/owner/**} operation that acts on a
 * {@code {restaurantId}}. Every owner service method calls {@link #requireOwnedRestaurant(Long)}
 * before touching the restaurant, its menu, its orders, or its analytics.
 *
 * <p>{@code SecurityConfig} already requires authentication for {@code /api/v1/owner/**} (an
 * anonymous caller gets {@code 401} at the filter chain), so this only has to answer "does the
 * authenticated caller own this restaurant?" — a caller who owns nothing, or a restaurant with no
 * owner assigned, both fail with {@code 403}.
 */
@Component
@RequiredArgsConstructor
public class RestaurantOwnershipGuard {

    private final RestaurantRepository restaurantRepository;
    private final UserContext userContext;

    /**
     * @return the restaurant, so callers can reuse the loaded entity
     * @throws RestaurantNotFoundException if no restaurant has that id ({@code 404})
     * @throws OwnerAccessDeniedException  if the authenticated caller is not its owner ({@code 403})
     * @throws com.food.foodapp.common.exception.UnauthenticatedException if the caller is anonymous ({@code 401})
     */
    @Transactional(readOnly = true)
    public Restaurant requireOwnedRestaurant(Long restaurantId) {
        Long callerId = userContext.getCurrentUserId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + restaurantId));

        Long ownerId = restaurant.getOwner() == null ? null : restaurant.getOwner().getId();
        if (!callerId.equals(ownerId)) {
            throw new OwnerAccessDeniedException(
                    "You do not have permission to manage restaurant " + restaurantId);
        }
        return restaurant;
    }
}
