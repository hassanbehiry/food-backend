package com.food.foodapp.coupon.entity;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One redemption of a {@link Coupon} — recorded exactly once, at order-creation time, by
 * {@code CouponService#recordUsage}. This is what {@code usageLimit} is enforced against: a
 * coupon's remaining uses is {@code usageLimit - count(CouponUsage where coupon = this)}.
 * <p>
 * {@code order_id} is unique so the same order can never redeem a coupon twice (an order has at
 * most one applied coupon, snapshotted on {@link Order#getCouponCode()}). Deliberately has no
 * mutation methods: like {@link com.food.foodapp.order.entity.OrderItem}, a usage record is
 * written once and never changed afterward.
 */
@Entity
@Table(name = "coupon_usages", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@NoArgsConstructor
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false, updatable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public CouponUsage(Coupon coupon, Order order, User customer) {
        this.coupon = coupon;
        this.order = order;
        this.customer = customer;
    }
}
