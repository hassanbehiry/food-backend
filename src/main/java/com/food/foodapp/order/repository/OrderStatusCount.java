package com.food.foodapp.order.repository;

import com.food.foodapp.order.entity.OrderStatus;

/**
 * One status's order count within a date range, as returned by
 * {@link OrderRepository#countGroupByStatusInRange} (admin, platform-wide) or
 * {@link OrderRepository#countByRestaurantIdGroupByStatusInRange} (owner, per-restaurant) — a
 * status with no orders in the range simply has no row, so callers building a complete breakdown
 * must zero-fill the statuses this doesn't return.
 */
public record OrderStatusCount(OrderStatus status, Long count) {
}
