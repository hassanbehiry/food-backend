package com.food.foodapp.order.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One order's timestamp and total — the raw material {@code AdminAnalyticsService} and
 * {@code OrderAnalyticsService} bucket by calendar day to build their revenue charts' day-by-day
 * series (see {@link OrderRepository#findRevenueLinesByStatusInRange} and
 * {@link OrderRepository#findRevenueLinesByRestaurantAndStatusInRange}). Bucketing happens in
 * application code rather than a JPQL {@code GROUP BY CAST(... AS date)}: Hibernate's
 * constructor-expression resolution for a {@code record} projection fails to match the database's
 * truncated-date column type back to a {@code LocalDate} constructor parameter
 * ({@code SemanticException: Missing constructor}), so this sidesteps that failure mode entirely
 * rather than fighting Hibernate's type inference for it.
 */
public record RevenueLine(LocalDateTime createdAt, BigDecimal total) {
}
