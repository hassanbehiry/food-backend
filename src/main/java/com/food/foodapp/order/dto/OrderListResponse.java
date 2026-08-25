package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Pagination envelope for the customer's order-history list — mirrors {@code OwnerOrderListResponse}'s shape. */
@Getter
@Builder
public class OrderListResponse {

    private List<OrderSummaryResponse> orders;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
