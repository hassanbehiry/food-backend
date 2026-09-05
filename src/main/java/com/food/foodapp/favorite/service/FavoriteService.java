package com.food.foodapp.favorite.service;

import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.UnauthenticatedException;
import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.dto.FavoriteToggleRequest;
import com.food.foodapp.favorite.dto.FavoriteToggleResponse;
import com.food.foodapp.favorite.entity.Favorite;
import com.food.foodapp.favorite.mapper.FavoriteMapper;
import com.food.foodapp.favorite.repository.FavoriteRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Customer restaurant favorites: list and toggle. The caller is always resolved via
 * {@link UserContext} — no method here accepts a customer id from the caller — and the
 * one existing-favorite lookup goes through {@link FavoriteRepository#findByCustomerIdAndRestaurantId},
 * so a favorite belonging to another customer can never be toggled.
 * <p>
 * {@link #toggleFavorite} takes the same {@link UserRepository#findByIdForUpdate} lock
 * {@code AddressService} uses: a customer can have zero-to-many favorites, so there is no
 * single row to lock the way a cart can lock its one-per-customer row, and the unique
 * ({@code customer_id}, {@code restaurant_id}) constraint alone would otherwise let two
 * concurrent taps on the same heart icon race — one succeeding, the other failing with a
 * constraint violation instead of the idempotent toggle the client expects.
 * <p>
 * Favoriting a restaurant that currently exists but is unavailable (unapproved, suspended,
 * or closed) is intentionally allowed — see {@link RestaurantService#requireRestaurant}. A
 * favorite is a customer's own bookmark, not evidence the restaurant is currently orderable,
 * so blocking it here would also block un-favoriting a restaurant that was favorited while
 * available and has since gone offline. Current availability is surfaced instead, at read
 * time, by {@link FavoriteMapper}.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final RestaurantService restaurantService;
    private final UserContext userContext;

    @Transactional(readOnly = true)
    public List<FavoriteResponse> listFavorites() {
        Long customerId = userContext.getCurrentUserId();
        List<Favorite> favorites = favoriteRepository.findByCustomerIdWithRestaurant(customerId);

        Map<Long, List<String>> categorySlugs = restaurantService.categorySlugsByRestaurantIds(
                favorites.stream().map(f -> f.getRestaurant().getId()).toList());

        return favorites.stream()
                .map(f -> FavoriteMapper.toResponse(f,
                        categorySlugs.getOrDefault(f.getRestaurant().getId(), List.of())))
                .toList();
    }

    @Transactional
    public FavoriteToggleResponse toggleFavorite(FavoriteToggleRequest request) {
        Long customerId = lockCustomer();
        Restaurant restaurant = restaurantService.requireRestaurant(request.getRestaurantId());

        Optional<Favorite> existing = favoriteRepository.findByCustomerIdAndRestaurantId(customerId, restaurant.getId());
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return FavoriteToggleResponse.builder().favorite(false).build();
        }

        Favorite favorite = new Favorite();
        favorite.setCustomer(userRepository.getReferenceById(customerId));
        favorite.setRestaurant(restaurant);
        favoriteRepository.save(favorite);
        return FavoriteToggleResponse.builder().favorite(true).build();
    }

    private Long lockCustomer() {
        Long customerId = userContext.getCurrentUserId();
        userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new UnauthenticatedException("Authentication required"));
        return customerId;
    }
}
