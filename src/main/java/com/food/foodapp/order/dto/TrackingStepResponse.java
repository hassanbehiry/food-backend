package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

/** One milestone in the customer-visible tracking progress: {@code CONFIRMED} → {@code DELIVERED}. */
@Getter
@Builder
public class TrackingStepResponse {

    private OrderStatus status;
    private boolean completed;
    private boolean current;
}
