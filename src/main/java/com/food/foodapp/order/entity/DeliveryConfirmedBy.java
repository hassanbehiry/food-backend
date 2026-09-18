package com.food.foodapp.order.entity;

/**
 * Which side confirmed an order's delivery — see {@link Order#getDeliveredBy()}. An order can reach
 * {@link OrderStatus#DELIVERED} via either the customer acknowledging receipt
 * ({@code OrderService#confirmDelivery}) or the restaurant/delivery side reporting hand-off
 * ({@code OrderService#deliverOrder}); this records which one actually happened, for audit purposes
 * — it has no effect on revenue recognition, which is identical either way.
 */
public enum DeliveryConfirmedBy {
    CUSTOMER,
    OWNER
}
