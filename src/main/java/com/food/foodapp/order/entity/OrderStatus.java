package com.food.foodapp.order.entity;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a placed {@link Order}. {@code CONFIRMED} is where every order starts: placing an
 * order is itself the customer's confirmation (there is no separate cart-side "pending" row to
 * accept first), so it immediately becomes visible to the restaurant.
 * <p>
 * From {@code CONFIRMED} the restaurant walks the order forward through its kitchen workflow —
 * {@code PREPARING} (kitchen is working on it) then {@code READY_FOR_DELIVERY} (food is ready for
 * courier pickup) — or calls it off early as {@code CANCELLED}. Once the restaurant dispatches the
 * order to a courier ({@code OrderService#sendToDelivery}, behind {@code POST
 * .../orders/{id}/send-to-delivery}) it becomes {@code OUT_FOR_DELIVERY}, from which there are two
 * independent, equally legal ways to reach the terminal {@code DELIVERED} state: the customer
 * confirming receipt ({@code OrderService#confirmDelivery}, behind {@code PUT
 * .../orders/{id}/confirm-delivery}) or the restaurant/delivery side confirming hand-off
 * ({@code OrderService#deliverOrder}, behind {@code POST .../orders/{id}/deliver}). Both routes
 * funnel through the same {@link #canTransitionTo(OrderStatus)} table and the same persisted
 * {@code status} column, so whichever confirmation lands first wins and the other is rejected as an
 * illegal (already-terminal) transition — there is no way for both to independently "win" and
 * double-count revenue.
 * <p>
 * {@link #canTransitionTo(OrderStatus)} is the single source of truth for which moves are legal, so
 * every status-changing method on {@code OrderService} — owner-driven kitchen progression,
 * dispatch, cancellation, and both delivery-confirmation paths — routes through the same table
 * instead of each hand-rolling their own rules.
 */
public enum OrderStatus {
    CONFIRMED,
    PREPARING,
    READY_FOR_DELIVERY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_NEXT = Map.of(
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(READY_FOR_DELIVERY, CANCELLED),
            READY_FOR_DELIVERY, Set.of(OUT_FOR_DELIVERY, CANCELLED),
            OUT_FOR_DELIVERY, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    /**
     * A customer may cancel an order any time it hasn't yet been dispatched to a courier — i.e.
     * {@code CONFIRMED}, {@code PREPARING}, or {@code READY_FOR_DELIVERY}. That rule falls straight
     * out of this transition table since {@code CANCELLED} is reachable from exactly those three
     * states (see {@code OrderService} for the narrower set the customer-facing endpoint actually
     * exposes today).
     */
    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}
