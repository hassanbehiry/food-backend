package com.food.foodapp.common.exception;

/** Thrown when an order is asked to move to a status its current status doesn't allow — see {@code OrderStatus} for the transition table. */
public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(String message) {
        super(message);
    }
}
