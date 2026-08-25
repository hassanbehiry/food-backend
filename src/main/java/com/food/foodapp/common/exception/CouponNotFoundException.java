package com.food.foodapp.common.exception;

/** Thrown when a submitted coupon code doesn't match any known coupon. */
public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException(String message) {
        super(message);
    }
}
