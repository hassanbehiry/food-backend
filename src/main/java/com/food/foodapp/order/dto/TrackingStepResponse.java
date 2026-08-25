package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

/** One milestone in the customer-visible tracking progress: {@code NEW} → {@code PREPARING} → {@code ON_THE_WAY} → {@code DELIVERED}. */
@Getter
@Builder
public class TrackingStepResponse {

    private OrderStatus status;
    private boolean completed;
    private boolean current;
}
