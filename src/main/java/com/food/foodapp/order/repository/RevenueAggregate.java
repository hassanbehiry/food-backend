package com.food.foodapp.order.repository;

import java.math.BigDecimal;

/**
 * Total revenue and order count for one status/date-range combination — the aggregate behind
 * the admin analytics overview's "revenue" KPI (see
 * {@link OrderRepository#sumRevenueByStatusInRange}).
 * <p>
 * {@code totalRevenue} is {@code null} rather than zero when no order matches, since JPQL's
 * {@code SUM} over an empty set is {@code NULL} (unlike {@code COUNT}, which is always {@code 0});
 * {@link #totalRevenueOrZero()} is the normalized accessor callers should use instead of the raw
 * field.
 */
public record RevenueAggregate(BigDecimal totalRevenue, Long orderCount) {

    public BigDecimal totalRevenueOrZero() {
        return totalRevenue == null ? BigDecimal.ZERO : totalRevenue;
    }
}
