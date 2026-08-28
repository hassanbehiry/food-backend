package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Pagination envelope for the owner orders list — mirrors {@code RestaurantListResponse}'s shape. */
@Getter
@Builder
public class OwnerOrderListResponse {

    private List<OwnerOrderSummaryResponse> orders;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
