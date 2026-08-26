package com.food.foodapp.restaurant.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Pagination envelope for the admin restaurants list — mirrors {@code RestaurantListResponse}'s shape. */
@Getter
@Builder
public class AdminRestaurantListResponse {

    private List<AdminRestaurantResponse> restaurants;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
