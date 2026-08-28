package com.food.foodapp.settings.mapper;

import com.food.foodapp.settings.dto.PlatformSettingsResponse;
import com.food.foodapp.settings.entity.PlatformSettings;

public final class PlatformSettingsMapper {

    private PlatformSettingsMapper() {
    }

    public static PlatformSettingsResponse toResponse(PlatformSettings settings) {
        return PlatformSettingsResponse.builder()
                .commissionPercentage(settings.getCommissionPercentage())
                .defaultDeliveryFee(settings.getDefaultDeliveryFee())
                .supportEmail(settings.getSupportEmail())
                .allowRestaurantRegistration(settings.isAllowRestaurantRegistration())
                .maintenanceMode(settings.isMaintenanceMode())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
