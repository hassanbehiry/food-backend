package com.food.foodapp.restaurant.mapper;

import com.food.foodapp.category.dto.CategoryResponse;
import com.food.foodapp.category.entity.Category;
import com.food.foodapp.category.mapper.CategoryMapper;
import com.food.foodapp.restaurant.dto.AdminRestaurantResponse;
import com.food.foodapp.restaurant.dto.OwnerRestaurantResponse;
import com.food.foodapp.restaurant.dto.RestaurantDetailResponse;
import com.food.foodapp.restaurant.dto.RestaurantSummaryResponse;
import com.food.foodapp.restaurant.entity.Restaurant;

import java.util.Comparator;
import java.util.List;

public final class RestaurantMapper {

    private RestaurantMapper() {
    }

    /**
     * Summary without category slugs — used where the caller has not loaded them (e.g. the
     * favorites list). The discovery list uses {@link #toSummary(Restaurant, List)}.
     */
    public static RestaurantSummaryResponse toSummary(Restaurant restaurant) {
        return toSummary(restaurant, List.of());
    }

    public static RestaurantSummaryResponse toSummary(Restaurant restaurant, List<String> categoryIds) {
        return RestaurantSummaryResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .cuisine(restaurant.getCuisine())
                .logoUrl(restaurant.getLogoUrl())
                .coverImageUrl(restaurant.getCoverImageUrl())
                .ratingAverage(restaurant.getRatingAverage())
                .reviewCount(restaurant.getReviewCount())
                .deliveryFee(restaurant.getDeliveryFee())
                .minimumOrder(restaurant.getMinimumOrder())
                .estimatedDeliveryMinMinutes(restaurant.getEstimatedDeliveryMinMinutes())
                .estimatedDeliveryMaxMinutes(restaurant.getEstimatedDeliveryMaxMinutes())
                .estimatedDeliveryLabel(formatDeliveryLabel(restaurant))
                .categoryIds(categoryIds)
                .openForOrders(restaurant.isOpenForOrders())
                .build();
    }

    public static RestaurantDetailResponse toDetail(Restaurant restaurant) {
        List<CategoryResponse> categories = restaurant.getCategories().stream()
                .map(CategoryMapper::toResponse)
                .sorted(Comparator.comparing(CategoryResponse::getName))
                .toList();

        List<String> categoryIds = restaurant.getCategories().stream()
                .map(Category::getSlug)
                .sorted()
                .toList();

        return RestaurantDetailResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .cuisine(restaurant.getCuisine())
                .logoUrl(restaurant.getLogoUrl())
                .coverImageUrl(restaurant.getCoverImageUrl())
                .ratingAverage(restaurant.getRatingAverage())
                .reviewCount(restaurant.getReviewCount())
                .deliveryFee(restaurant.getDeliveryFee())
                .minimumOrder(restaurant.getMinimumOrder())
                .estimatedDeliveryMinMinutes(restaurant.getEstimatedDeliveryMinMinutes())
                .estimatedDeliveryMaxMinutes(restaurant.getEstimatedDeliveryMaxMinutes())
                .estimatedDeliveryLabel(formatDeliveryLabel(restaurant))
                .openForOrders(restaurant.isOpenForOrders())
                .categoryIds(categoryIds)
                .categories(categories)
                .build();
    }

    public static OwnerRestaurantResponse toOwnerResponse(Restaurant restaurant) {
        return OwnerRestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .cuisine(restaurant.getCuisine())
                .deliveryFee(restaurant.getDeliveryFee())
                .minimumOrder(restaurant.getMinimumOrder())
                .openTime(restaurant.getOpenTime())
                .closeTime(restaurant.getCloseTime())
                .openForOrders(restaurant.isOpenForOrders())
                .build();
    }

    public static AdminRestaurantResponse toAdminResponse(Restaurant restaurant) {
        return AdminRestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .cuisine(restaurant.getCuisine())
                .deliveryFee(restaurant.getDeliveryFee())
                .minimumOrder(restaurant.getMinimumOrder())
                .openTime(restaurant.getOpenTime())
                .closeTime(restaurant.getCloseTime())
                .openForOrders(restaurant.isOpenForOrders())
                .approvalStatus(restaurant.getApprovalStatus())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }

    private static String formatDeliveryLabel(Restaurant restaurant) {
        return restaurant.getEstimatedDeliveryMinMinutes() + "-" + restaurant.getEstimatedDeliveryMaxMinutes() + " دقيقة";
    }
}
