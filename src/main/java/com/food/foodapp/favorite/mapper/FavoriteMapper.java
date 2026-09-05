package com.food.foodapp.favorite.mapper;

import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.entity.Favorite;
import com.food.foodapp.restaurant.mapper.RestaurantMapper;
import com.food.foodapp.restaurant.service.RestaurantService;

import java.util.List;

public final class FavoriteMapper {

    private FavoriteMapper() {
    }

    /**
     * @param categoryIds the favorited restaurant's category slugs — resolved by the caller in one
     *                    batch query for the whole list (see
     *                    {@code RestaurantService.categorySlugsByRestaurantIds}).
     */
    public static FavoriteResponse toResponse(Favorite favorite, List<String> categoryIds) {
        return FavoriteResponse.builder()
                .restaurant(RestaurantMapper.toSummary(favorite.getRestaurant(), categoryIds))
                .available(RestaurantService.isCustomerVisible(favorite.getRestaurant()))
                .favoritedAt(favorite.getCreatedAt())
                .build();
    }
}
