package com.food.foodapp.order.entity;

/**
 * The kind of ledger entry a {@link RevenueTransaction} row represents. Only {@code ORDER_PAYMENT}
 * exists today — one row per delivered order, written the moment it reaches
 * {@link OrderStatus#DELIVERED} — but the column is a real enum rather than a hardcoded literal so a
 * future adjustment/refund/correction type can be added without a schema change.
 */
public enum RevenueTransactionType {
    ORDER_PAYMENT
}
