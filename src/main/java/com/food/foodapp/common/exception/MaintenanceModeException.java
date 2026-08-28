package com.food.foodapp.common.exception;

/** Thrown when a customer attempts to preview or place an order while platform-wide maintenance mode is enabled. */
public class MaintenanceModeException extends RuntimeException {

    public MaintenanceModeException(String message) {
        super(message);
    }
}
