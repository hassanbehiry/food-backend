package com.food.foodapp.favorite.mapper;

import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.entity.Favorite;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteMapperTest {

    @Test
    void toResponse_marksAvailable_forApprovedAndOpenRestaurant() {
        Restaurant restaurant = restaurant(RestaurantApprovalStatus.APPROVED, true);
        Favorite favorite = favorite(restaurant);

        FavoriteResponse response = FavoriteMapper.toResponse(favorite, List.of("italian", "pizza"));

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getRestaurant().getId()).isEqualTo(restaurant.getId());
        assertThat(response.getRestaurant().getCategoryIds()).containsExactly("italian", "pizza");
        assertThat(response.getFavoritedAt()).isEqualTo(favorite.getCreatedAt());
    }

    @Test
    void toResponse_marksUnavailable_forSuspendedRestaurant() {
        Restaurant restaurant = restaurant(RestaurantApprovalStatus.SUSPENDED, true);

        FavoriteResponse response = FavoriteMapper.toResponse(favorite(restaurant), List.of());

        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    void toResponse_marksUnavailable_forApprovedButClosedRestaurant() {
        Restaurant restaurant = restaurant(RestaurantApprovalStatus.APPROVED, false);

        FavoriteResponse response = FavoriteMapper.toResponse(favorite(restaurant), List.of());

        assertThat(response.isAvailable()).isFalse();
    }

    private Favorite favorite(Restaurant restaurant) {
        Favorite favorite = new Favorite();
        favorite.setId(1L);
        favorite.setRestaurant(restaurant);
        favorite.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return favorite;
    }

    private Restaurant restaurant(RestaurantApprovalStatus approvalStatus, boolean openForOrders) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(10L);
        restaurant.setName("Test Restaurant");
        restaurant.setCuisine("Italian");
        restaurant.setRatingAverage(BigDecimal.valueOf(4.5));
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(50));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setApprovalStatus(approvalStatus);
        restaurant.setOpenForOrders(openForOrders);
        return restaurant;
    }
}
