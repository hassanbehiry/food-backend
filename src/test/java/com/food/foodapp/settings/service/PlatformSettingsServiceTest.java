package com.food.foodapp.settings.service;

import com.food.foodapp.settings.dto.PlatformSettingsUpdateRequest;
import com.food.foodapp.settings.entity.PlatformSettings;
import com.food.foodapp.settings.repository.PlatformSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSettingsServiceTest {

    @Mock
    private PlatformSettingsRepository platformSettingsRepository;

    private PlatformSettingsService platformSettingsService;

    @BeforeEach
    void setUp() {
        platformSettingsService = new PlatformSettingsService(platformSettingsRepository);
    }

    @Test
    void getSettings_returnsJavaSideDefaults_whenNoRowPersistedYet() {
        when(platformSettingsRepository.findById(PlatformSettings.ID)).thenReturn(Optional.empty());

        var response = platformSettingsService.getSettings();

        assertThat(response.getCommissionPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.isAllowRestaurantRegistration()).isTrue();
        assertThat(response.isMaintenanceMode()).isFalse();
    }

    @Test
    void getSettings_returnsPersistedRow_whenOneExists() {
        PlatformSettings settings = new PlatformSettings();
        settings.setCommissionPercentage(BigDecimal.valueOf(12.5));
        settings.setMaintenanceMode(true);
        when(platformSettingsRepository.findById(PlatformSettings.ID)).thenReturn(Optional.of(settings));

        var response = platformSettingsService.getSettings();

        assertThat(response.getCommissionPercentage()).isEqualByComparingTo(BigDecimal.valueOf(12.5));
        assertThat(response.isMaintenanceMode()).isTrue();
    }

    @Test
    void updateSettings_createsRow_whenNoneExistsYet() {
        when(platformSettingsRepository.findById(PlatformSettings.ID)).thenReturn(Optional.empty());
        when(platformSettingsRepository.save(any(PlatformSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = platformSettingsService.updateSettings(updateRequest());

        assertThat(response.getCommissionPercentage()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getDefaultDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(response.getSupportEmail()).isEqualTo("support@wajba.com");
        assertThat(response.isAllowRestaurantRegistration()).isFalse();
        assertThat(response.isMaintenanceMode()).isTrue();
    }

    @Test
    void updateSettings_updatesExistingRow() {
        PlatformSettings existing = new PlatformSettings();
        when(platformSettingsRepository.findById(PlatformSettings.ID)).thenReturn(Optional.of(existing));
        when(platformSettingsRepository.save(any(PlatformSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        platformSettingsService.updateSettings(updateRequest());

        assertThat(existing.getCommissionPercentage()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(existing.isMaintenanceMode()).isTrue();
    }

    @Test
    void isRestaurantRegistrationAllowed_reflectsPersistedRow() {
        PlatformSettings settings = new PlatformSettings();
        settings.setAllowRestaurantRegistration(false);
        when(platformSettingsRepository.findById(PlatformSettings.ID)).thenReturn(Optional.of(settings));

        assertThat(platformSettingsService.isRestaurantRegistrationAllowed()).isFalse();
    }

    @Test
    void isMaintenanceModeEnabled_defaultsToFalse_whenNoRowPersistedYet() {
        when(platformSettingsRepository.findById(PlatformSettings.ID)).thenReturn(Optional.empty());

        assertThat(platformSettingsService.isMaintenanceModeEnabled()).isFalse();
    }

    private PlatformSettingsUpdateRequest updateRequest() {
        PlatformSettingsUpdateRequest request = new PlatformSettingsUpdateRequest();
        request.setCommissionPercentage(BigDecimal.TEN);
        request.setDefaultDeliveryFee(BigDecimal.valueOf(15));
        request.setSupportEmail("support@wajba.com");
        request.setAllowRestaurantRegistration(false);
        request.setMaintenanceMode(true);
        return request;
    }
}
