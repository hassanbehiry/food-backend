package com.food.foodapp.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One order's timestamp and total — the raw material {@code AdminAnalyticsService} buckets by
 * calendar day to build the admin revenue chart's day-by-day series (see
 * {@link OrderRepository#findRevenueLinesByStatusInRange}). Bucketing happens in application code
 * rather than a JPQL {@code GROUP BY CAST(... AS date)} for the same Hibernate
 * constructor-expression reason documented on
 * {@link OrderRepository#findCreatedAtInRange}.
 */
public record RevenueLine(LocalDateTime createdAt, BigDecimal total) {
}
