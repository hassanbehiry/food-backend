package com.food.foodapp.restaurant.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RestaurantListResponse {

    private List<RestaurantSummaryResponse> restaurants;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
