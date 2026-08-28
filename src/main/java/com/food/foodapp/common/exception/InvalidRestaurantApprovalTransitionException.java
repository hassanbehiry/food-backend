package com.food.foodapp.common.exception;

/** Thrown when a restaurant is asked to move to an approval status its current status doesn't allow — see {@code RestaurantApprovalStatus} for the transition table. */
public class InvalidRestaurantApprovalTransitionException extends RuntimeException {

    public InvalidRestaurantApprovalTransitionException(String message) {
        super(message);
    }
}
