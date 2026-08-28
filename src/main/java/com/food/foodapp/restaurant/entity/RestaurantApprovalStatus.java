package com.food.foodapp.restaurant.entity;

import java.util.Map;
import java.util.Set;

/**
 * Platform-admin approval/suspension state. Independent of the owner-controlled
 * {@code openForOrders} toggle — approval governs whether a restaurant may appear
 * on the platform at all, not whether it is currently accepting orders.
 * <p>
 * {@link #canTransitionTo(RestaurantApprovalStatus)} is the single source of truth for which
 * moves are legal, mirroring {@code OrderStatus}. There is no separate "restore" state: an
 * admin restores a {@code SUSPENDED} restaurant via the same {@code APPROVED} target that a
 * first-time or post-rejection approval uses, since both mean "this restaurant is active."
 */
public enum RestaurantApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    private static final Map<RestaurantApprovalStatus, Set<RestaurantApprovalStatus>> ALLOWED_NEXT = Map.of(
            PENDING, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(SUSPENDED),
            REJECTED, Set.of(APPROVED),
            SUSPENDED, Set.of(APPROVED));

    public boolean canTransitionTo(RestaurantApprovalStatus target) {
        return ALLOWED_NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}
