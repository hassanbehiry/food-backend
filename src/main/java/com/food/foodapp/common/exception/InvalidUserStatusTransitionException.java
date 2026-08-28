package com.food.foodapp.common.exception;

/** Thrown when a user account is asked to move to a status its current status doesn't allow — see {@code UserStatus} for the transition table. */
public class InvalidUserStatusTransitionException extends RuntimeException {

    public InvalidUserStatusTransitionException(String message) {
        super(message);
    }
}
