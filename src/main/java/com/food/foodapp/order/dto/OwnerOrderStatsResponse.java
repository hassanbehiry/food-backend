package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

/** Per-status counts backing the owner dashboard's badge numbers, plus the restaurant's order total. */
@Getter
@Builder
public class OwnerOrderStatsResponse {

    private long confirmedCount;
    private long deliveredCount;
    private long cancelledCount;
    private long totalCount;
}
