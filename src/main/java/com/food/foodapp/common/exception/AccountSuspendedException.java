package com.food.foodapp.common.exception;

/** Thrown when a suspended account attempts to authenticate or place an order. */
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException(String message) {
        super(message);
    }
}
