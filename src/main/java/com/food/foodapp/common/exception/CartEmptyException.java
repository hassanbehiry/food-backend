package com.food.foodapp.common.exception;

/** Thrown when checkout preview or order creation is attempted against an empty (or nonexistent) cart. */
public class CartEmptyException extends RuntimeException {

    public CartEmptyException(String message) {
        super(message);
    }
}
