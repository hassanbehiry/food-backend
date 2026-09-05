package com.food.foodapp.common.exception;

/**
 * Thrown when an order already has a review — one review per order. Maps to {@code 409} via
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException(String message) {
        super(message);
    }
}
