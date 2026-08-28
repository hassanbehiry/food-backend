package com.food.foodapp.order.entity;

/**
 * Only cash on delivery is in scope: the frontend's checkout page currently renders exactly one
 * payment option with no online-payment alternative. Kept as its own enum (rather than a raw
 * string) so a real payment method can be added later without changing the {@link Order} schema.
 */
public enum PaymentMethod {
    CASH_ON_DELIVERY
}
