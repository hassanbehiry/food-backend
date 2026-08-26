package com.food.foodapp.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Platform-wide configuration the admin dashboard's Settings tab reads and writes. This is a
 * singleton row — {@link #ID} is the only id ever used — rather than a per-tenant table, since
 * this codebase has exactly one platform. {@link com.food.foodapp.settings.service.PlatformSettingsService}
 * never persists a row until the first {@code PUT}; until then, callers see the Java-side defaults
 * below rather than a seeded database row.
 */
@Entity
@Table(name = "platform_settings")
@Check(constraints = "commission_percentage >= 0 AND commission_percentage <= 100 AND default_delivery_fee >= 0")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSettings {

    public static final Long ID = 1L;

    @Id
    private Long id = ID;

    @Column(name = "commission_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercentage = BigDecimal.ZERO;

    @Column(name = "default_delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultDeliveryFee = BigDecimal.ZERO;

    @Column(name = "support_email", length = 150)
    private String supportEmail;

    /** Gates new restaurant/owner self-registration specifically — not general customer sign-up. */
    @Column(name = "allow_restaurant_registration", nullable = false)
    private boolean allowRestaurantRegistration = true;

    /** When enabled, customer ordering is temporarily disabled platform-wide — see {@code OrderService#computeOrder}. */
    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode = false;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
