package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

/** One row of an analytics overview's "orders by status" breakdown (admin platform-wide or owner per-restaurant). */
@Getter
@Builder
public class OrderStatusCountResponse {

    private OrderStatus status;
    private long count;
}
