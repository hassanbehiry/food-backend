package com.food.foodapp.common.exception;

/**
 * Thrown when an order exists and belongs to the caller but is not in a state that permits a
 * review — currently: the order has not been {@code DELIVERED} yet. Maps to {@code 409} via
 * {@link GlobalExceptionHandler}.
 */
public class ReviewNotEligibleException extends RuntimeException {

    public ReviewNotEligibleException(String message) {
        super(message);
    }
}
