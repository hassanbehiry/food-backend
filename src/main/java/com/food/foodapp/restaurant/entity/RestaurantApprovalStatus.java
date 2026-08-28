package com.food.foodapp.restaurant.entity;

/**
 * Platform-admin approval/suspension state. Independent of the owner-controlled
 * {@code openForOrders} toggle — approval governs whether a restaurant may appear
 * on the platform at all, not whether it is currently accepting orders.
 */
public enum RestaurantApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}
