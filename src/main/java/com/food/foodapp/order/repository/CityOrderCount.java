package com.food.foodapp.order.repository;

/**
 * One delivery city's order count within a date range, as returned by
 * {@link OrderRepository#countGroupByCityInRange} — the raw material behind the admin
 * analytics "orders by city" donut. {@code city} is {@link com.food.foodapp.order.entity.Order#getDeliveryCity()},
 * the free-text delivery-address city snapshotted at order-creation time, so it reflects
 * exactly the spelling/casing the customer's address was saved with; {@code AdminAnalyticsService}
 * is responsible for bucketing the long tail into "Other", not this projection.
 */
public record CityOrderCount(String city, Long count) {
}
