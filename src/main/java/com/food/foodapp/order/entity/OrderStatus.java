package com.food.foodapp.order.entity;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a placed {@link Order}. This is the single canonical vocabulary for order status —
 * it reconciles two inconsistent frontend vocabularies that both describe the same lifecycle: the
 * owner dashboard's Arabic-keyed tabs (جديد "New" → قيد التحضير "Preparing" → في الطريق "On the
 * way" → مكتمل "Completed", with no distinct "Accepted" tab) and the admin dashboard's
 * English-keyed list (Preparing → On the way → Delivered → Cancelled). {@code DELIVERED} was
 * picked over "Completed" for the final successful state since it matches the admin dashboard's
 * existing wording and the everyday meaning better.
 * <p>
 * {@code CONFIRMED} sits between {@code NEW} and {@code PREPARING} even though neither dashboard
 * surfaces a distinct "Accepted" tab: {@link com.food.foodapp.order.service.OrderService}'s
 * customer-cancellation window already depends on it as a separate reachable state (a customer may
 * cancel while {@code NEW} or {@code CONFIRMED}, but not once {@code PREPARING} has started), so it
 * is kept as an internal transition rather than collapsed away. The owner-driven "start preparing"
 * action treats accepting an order as the same single action as moving it to {@code PREPARING} —
 * see {@code OrderService#updateOrderStatus}, which advances a {@code NEW} order through
 * {@code CONFIRMED} to {@code PREPARING} in one call so the owner API needs no separate "accept"
 * step the dashboard doesn't have a button for.
 * <p>
 * {@link #canTransitionTo(OrderStatus)} is the single source of truth for which moves are legal, so
 * both the owner-driven forward workflow and the customer-initiated cancellation in
 * {@code OrderService} route through the same table instead of each hand-rolling their own rules.
 */
public enum OrderStatus {
    NEW,
    CONFIRMED,
    PREPARING,
    ON_THE_WAY,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_NEXT = Map.of(
            NEW, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(ON_THE_WAY),
            ON_THE_WAY, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    /**
     * A customer may only cancel while the order is still {@code NEW} or {@code CONFIRMED} —
     * i.e. before the restaurant has started {@code PREPARING} it. That rule falls straight out
     * of this transition table since {@code CANCELLED} is only reachable from those two states.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_NEXT.getOrDefault(this, Set.of()).contains(target);
    }
}
