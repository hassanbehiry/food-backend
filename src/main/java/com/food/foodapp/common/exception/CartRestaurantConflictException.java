package com.food.foodapp.common.exception;

/** Thrown when adding an item would mix two restaurants into one cart — see CartService for the one-restaurant-per-cart rule. */
public class CartRestaurantConflictException extends RuntimeException {

    public CartRestaurantConflictException(String message) {
        super(message);
    }
}
