package com.food.foodapp.common.exception;

/**
 * Thrown when a review cannot be located. The {@code POST /api/v1/orders/{orderId}/reviews} write
 * path signals a missing target order with {@link OrderNotFoundException} (reused, already a 404);
 * this type exists for review-scoped lookups and maps to {@code 404} via
 * {@link GlobalExceptionHandler}.
 */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(String message) {
        super(message);
    }
}
