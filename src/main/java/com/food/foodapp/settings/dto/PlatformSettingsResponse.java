package com.food.foodapp.settings.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PlatformSettingsResponse {

    private BigDecimal commissionPercentage;
    private BigDecimal defaultDeliveryFee;
    private String supportEmail;
    private boolean allowRestaurantRegistration;
    private boolean maintenanceMode;
    private LocalDateTime updatedAt;
}
