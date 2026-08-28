package com.food.foodapp.common.exception;

/** Thrown when an OWNER attempts to register while the admin has disabled new restaurant self-registration. */
public class RestaurantRegistrationClosedException extends RuntimeException {

    public RestaurantRegistrationClosedException(String message) {
        super(message);
    }
}
