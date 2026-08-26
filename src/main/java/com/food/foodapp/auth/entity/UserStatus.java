package com.food.foodapp.auth.entity;

import java.util.Map;
import java.util.Set;

/**
 * Platform-admin account status. {@code SUSPENDED} blocks both authentication ({@code AuthService#login})
 * and order placement ({@code OrderService#computeOrder}) — see those call sites for enforcement.
 * <p>
 * {@link #canTransitionTo(UserStatus)} is the single source of truth for which moves are legal,
 * mirroring {@code RestaurantApprovalStatus}: re-requesting the status a user is already in is
 * not a legal transition, so a client can't silently no-op a stale "suspend" request onto an
 * already-suspended account.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED;

    private static final Map<UserStatus, Set<UserStatus>> ALLOWED_NEXT = Map.of(
            ACTIVE, Set.of(SUSPENDED),
            SUSPENDED, Set.of(ACTIVE));

    public boolean canTransitionTo(UserStatus target) {
        return ALLOWED_NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}
