package com.food.foodapp.coupon.entity;

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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A discount code the backend validates and applies during checkout — never trusted or
 * calculated on the frontend (see {@code com.food.foodapp.coupon.service.CouponService}).
 * {@code code} is always stored upper-cased so lookups are a plain equality match rather than a
 * case-insensitive query.
 * <p>
 * {@code restaurant} is null for a platform-wide coupon and non-null for one scoped to a single
 * restaurant, mirroring how {@link com.food.foodapp.cart.entity.Cart#getRestaurant()} models
 * "no restaurant yet" as null rather than a separate flag.
 * <p>
 * {@code startsAt}/{@code endsAt} are both nullable: null {@code startsAt} means "valid
 * immediately", null {@code endsAt} means "no expiry". {@code usageLimit} null means unlimited
 * redemptions; when set, it is enforced against {@link CouponUsage} rows recorded once per order
 * that actually redeemed this coupon (see {@code CouponService#recordUsage}).
 */
@Entity
@Table(name = "coupons")
@Check(constraints = "discount_value > 0 "
        + "AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100) "
        + "AND (min_order_amount IS NULL OR min_order_amount >= 0) "
        + "AND (max_discount_amount IS NULL OR max_discount_amount > 0) "
        + "AND (usage_limit IS NULL OR usage_limit > 0) "
        + "AND (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean active = true;

    /** Null = platform-wide; non-null = redeemable only against this restaurant's cart. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
