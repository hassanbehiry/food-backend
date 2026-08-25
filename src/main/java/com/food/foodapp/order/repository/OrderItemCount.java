package com.food.foodapp.order.repository;

/**
 * One order's total item quantity across all its lines — computed by
 * {@link OrderRepository#sumItemQuantitiesByOrderIds} as a separate query rather than a fetch
 * join on {@code Order.items}, so paginating the customer's order history never multiplies result
 * rows or risks Hibernate's "cannot simultaneously fetch multiple bags" error.
 */
public record OrderItemCount(Long orderId, Long itemCount) {
}
