package com.food.foodapp.order.entity;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a placed {@link Order}. This codebase has no owner-driven status-update endpoint
 * yet (accepting/preparing/dispatching an order from the restaurant side is a separate,
 * still-unbuilt feature) — {@link #canTransitionTo(OrderStatus)} is written now as the single
 * source of truth for which moves are legal so that future workflow, and the customer-initiated
 * cancellation implemented in {@code OrderService} today, both route through the same table
 * instead of each hand-rolling their own rules.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_NEXT = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    /**
     * A customer may only cancel while the order is still {@code PENDING} or {@code CONFIRMED} —
     * i.e. before the restaurant has started {@code PREPARING} it. That rule falls straight out
     * of this transition table since {@code CANCELLED} is only reachable from those two states.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}
