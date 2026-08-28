package com.food.foodapp.common.exception;

/**
 * Thrown when a coupon exists but its current state makes it unusable for this attempt: inactive,
 * outside its validity window, below the minimum order amount, scoped to a different restaurant,
 * or its usage limit has been reached. Kept as one exception type covering every such reason
 * (each with its own message) rather than one class per reason, since all of them mean the same
 * thing to the caller — this coupon cannot be applied right now.
 */
public class CouponNotApplicableException extends RuntimeException {

    public CouponNotApplicableException(String message) {
        super(message);
    }
}
