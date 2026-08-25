package com.food.foodapp.coupon.entity;

/** How a {@link Coupon}'s {@code discountValue} is interpreted. */
public enum DiscountType {
    /** {@code discountValue} is a percentage (0-100] of the order subtotal, optionally capped by {@code maxDiscountAmount}. */
    PERCENTAGE,
    /** {@code discountValue} is a flat currency amount, capped at the order subtotal so a discount can never exceed it. */
    FIXED
}
