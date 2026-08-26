package com.food.foodapp.favorite.mapper;

import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.entity.Favorite;
import com.food.foodapp.restaurant.mapper.RestaurantMapper;
import com.food.foodapp.restaurant.service.RestaurantService;

public final class FavoriteMapper {

    private FavoriteMapper() {
    }

    public static FavoriteResponse toResponse(Favorite favorite) {
        return FavoriteResponse.builder()
                .restaurant(RestaurantMapper.toSummary(favorite.getRestaurant()))
                .available(RestaurantService.isCustomerVisible(favorite.getRestaurant()))
                .favoritedAt(favorite.getCreatedAt())
                .build();
    }
}
