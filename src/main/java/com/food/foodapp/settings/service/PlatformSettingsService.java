package com.food.foodapp.settings.service;

import com.food.foodapp.settings.dto.PlatformSettingsResponse;
import com.food.foodapp.settings.dto.PlatformSettingsUpdateRequest;
import com.food.foodapp.settings.entity.PlatformSettings;
import com.food.foodapp.settings.mapper.PlatformSettingsMapper;
import com.food.foodapp.settings.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-wide settings: a single {@link PlatformSettings} row ({@link PlatformSettings#ID})
 * that the admin dashboard's Settings tab reads and writes, and that other domain services read
 * to gate their own runtime behavior (see {@link #isMaintenanceModeEnabled()} and
 * {@link #isRestaurantRegistrationAllowed()}) — those services depend on this class rather than
 * querying {@link PlatformSettingsRepository} themselves, so there is exactly one place that
 * knows how to resolve "current settings".
 * <p>
 * No row is persisted until the first {@link #updateSettings}: read-only callers before that
 * point see the in-memory defaults on a fresh {@link PlatformSettings} instance, which avoids
 * ever needing to write from inside a {@code readOnly} transaction.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository platformSettingsRepository;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse getSettings() {
        return PlatformSettingsMapper.toResponse(currentOrDefaults());
    }

    @Transactional
    public PlatformSettingsResponse updateSettings(PlatformSettingsUpdateRequest request) {
        PlatformSettings settings = platformSettingsRepository.findById(PlatformSettings.ID)
                .orElseGet(PlatformSettings::new);

        settings.setCommissionPercentage(request.getCommissionPercentage());
        settings.setDefaultDeliveryFee(request.getDefaultDeliveryFee());
        settings.setSupportEmail(request.getSupportEmail().trim());
        settings.setAllowRestaurantRegistration(request.getAllowRestaurantRegistration());
        settings.setMaintenanceMode(request.getMaintenanceMode());

        return PlatformSettingsMapper.toResponse(platformSettingsRepository.save(settings));
    }

    /** Consumed by {@code AuthService#register} to gate new restaurant/owner self-registration. */
    @Transactional(readOnly = true)
    public boolean isRestaurantRegistrationAllowed() {
        return currentOrDefaults().isAllowRestaurantRegistration();
    }

    /** Consumed by {@code OrderService#computeOrder} to gate customer ordering platform-wide. */
    @Transactional(readOnly = true)
    public boolean isMaintenanceModeEnabled() {
        return currentOrDefaults().isMaintenanceMode();
    }

    private PlatformSettings currentOrDefaults() {
        return platformSettingsRepository.findById(PlatformSettings.ID).orElseGet(PlatformSettings::new);
    }
}
