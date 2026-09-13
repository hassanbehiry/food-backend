package com.food.foodapp.restaurant.entity;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.category.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A restaurant available for customer discovery.
 * <p>
 * Whether a restaurant is currently accepting orders is derived entirely from
 * {@code openTime}/{@code closeTime} (see {@link #isCurrentlyOpen()}) — there is no separate
 * owner-settable "open/closed" switch. {@code approvalStatus} remains a distinct, admin-owned
 * concern (see {@link RestaurantApprovalStatus}) and is never collapsed into this.
 */
@Entity
@Table(name = "restaurants")
@Check(constraints = "estimated_delivery_max_minutes >= estimated_delivery_min_minutes "
        + "AND delivery_fee >= 0 AND minimum_order >= 0 "
        + "AND (open_time IS NULL OR close_time IS NULL OR close_time > open_time)")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** Free-text display label (e.g. "إيطالي · بيتزا") — distinct from the {@link Category} taxonomy. */
    @Column(nullable = false, length = 150)
    private String cuisine;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "minimum_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrder;

    @Column(name = "estimated_delivery_min_minutes", nullable = false)
    private int estimatedDeliveryMinMinutes;

    @Column(name = "estimated_delivery_max_minutes", nullable = false)
    private int estimatedDeliveryMaxMinutes;

    /** Daily opening time. Owner-editable; {@code null} until the owner sets business hours. */
    @Column(name = "open_time")
    private LocalTime openTime;

    /** Daily closing time. Owner-editable; {@code null} until the owner sets business hours. */
    @Column(name = "close_time")
    private LocalTime closeTime;

    /** Admin-controlled approval/suspension state. Not derived from business hours. */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private RestaurantApprovalStatus approvalStatus = RestaurantApprovalStatus.PENDING;

    /**
     * The user who owns and manages this restaurant. {@code null} for legacy/seed rows that
     * predate ownership — those are unmanageable through {@code /api/v1/owner/**} (every call
     * 403s via {@link com.food.foodapp.restaurant.service.RestaurantOwnershipGuard}) until an
     * owner is assigned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "restaurant_categories",
            joinColumns = @JoinColumn(name = "restaurant_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Whether the restaurant is inside its posted business hours right now. No schedule set
     * (either bound {@code null}) means always open, matching the previous default of accepting
     * orders until the owner configures hours. A pure function of this entity's own fields, unlike
     * {@code RestaurantService.isCustomerVisible}/{@code isCustomerReadable}, which combine this
     * with the admin-owned approval state and stay in the service layer on purpose.
     */
    public boolean isCurrentlyOpen() {
        if (openTime == null || closeTime == null) {
            return true;
        }
        LocalTime now = LocalTime.now();
        return !now.isBefore(openTime) && now.isBefore(closeTime);
    }
}
