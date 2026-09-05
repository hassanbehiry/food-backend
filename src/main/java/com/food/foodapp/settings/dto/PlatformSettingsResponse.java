package com.food.foodapp.settings.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The response carries each renamed field under BOTH names: the canonical
 * {@code commissionPercentage} / {@code allowRestaurantRegistration} and the shorter
 * {@code commission} / {@code allowRegistration} the frontend admin dashboard reads.
 */
@Getter
@Builder
public class PlatformSettingsResponse {

    private BigDecimal commissionPercentage;
    private BigDecimal defaultDeliveryFee;
    private String supportEmail;
    private boolean allowRestaurantRegistration;
    private boolean maintenanceMode;
    private LocalDateTime updatedAt;

    @JsonProperty("commission")
    public BigDecimal getCommission() {
        return commissionPercentage;
    }

    @JsonProperty("allowRegistration")
    public boolean isAllowRegistration() {
        return allowRestaurantRegistration;
    }
}
