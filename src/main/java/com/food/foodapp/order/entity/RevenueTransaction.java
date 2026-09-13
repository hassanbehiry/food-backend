package com.food.foodapp.order.entity;

import com.food.foodapp.restaurant.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The restaurant's revenue ledger: one immutable row per order that has actually been delivered.
 * {@code OrderAnalyticsService}'s dashboard/analytics figures are computed independently as a live
 * {@code SUM} over {@code orders} where {@code status = DELIVERED} (see its class javadoc) — this
 * table does not replace that computation, it exists alongside it as an auditable, append-only
 * record of exactly when each order's revenue was recognized and by whom (see
 * {@code OrderService#deliverOrder}).
 * <p>
 * {@code order} carries a {@code UNIQUE} database constraint (see migration {@code V14}), which is
 * the hard, DB-enforced backstop against ever recording the same order's revenue twice — the
 * {@link OrderStatus} state machine already makes a second {@code DELIVERED} transition impossible
 * in the normal case (see {@link OrderStatus}'s javadoc), but the unique constraint means even a
 * concurrent-write race can never produce two rows for one order.
 */
@Entity
@Table(name = "revenue_transactions")
@Getter
@Setter
@NoArgsConstructor
public class RevenueTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RevenueTransactionType type = RevenueTransactionType.ORDER_PAYMENT;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
