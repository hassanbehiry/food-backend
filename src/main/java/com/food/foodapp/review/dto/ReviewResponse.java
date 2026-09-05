package com.food.foodapp.review.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** A persisted per-order review, returned by {@code POST /api/v1/orders/{orderId}/reviews}. */
@Getter
@Builder
public class ReviewResponse {

    private Long id;

    private Long orderId;

    private Long restaurantId;

    private int rating;

    private String comment;

    private LocalDateTime createdAt;
}
