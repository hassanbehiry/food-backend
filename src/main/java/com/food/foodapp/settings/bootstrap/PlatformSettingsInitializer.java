package com.food.foodapp.settings.bootstrap;

import com.food.foodapp.settings.entity.PlatformSettings;
import com.food.foodapp.settings.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds the singleton {@link PlatformSettings} row on startup if it is missing.
 * <p>
 * Without this, a fresh install has no row until the first {@code PUT /api/v1/admin/settings};
 * {@code GET} then returns {@code supportEmail: null}, but {@code PUT} requires
 * {@code supportEmail @NotBlank @Email} — so an admin who hydrates the form from {@code GET} and
 * saves without re-typing the email gets a 400. Seeding a valid default row removes that trap.
 * <p>
 * Idempotent: does nothing when the row already exists. Mirrors
 * {@link com.food.foodapp.auth.bootstrap.AdminAccountInitializer}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformSettingsInitializer implements ApplicationRunner {

    private final PlatformSettingsRepository platformSettingsRepository;

    @Value("${app.platform-settings.default-support-email:support@wajba.local}")
    private String defaultSupportEmail;

    @Value("${app.platform-settings.default-delivery-fee:15.00}")
    private BigDecimal defaultDeliveryFee;

    @Value("${app.platform-settings.default-commission-percentage:0.00}")
    private BigDecimal defaultCommissionPercentage;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (platformSettingsRepository.existsById(PlatformSettings.ID)) {
            log.debug("Platform settings seed skipped: singleton row already present");
            return;
        }

        PlatformSettings settings = new PlatformSettings();
        settings.setCommissionPercentage(defaultCommissionPercentage);
        settings.setDefaultDeliveryFee(defaultDeliveryFee);
        settings.setSupportEmail(defaultSupportEmail.trim());
        settings.setAllowRestaurantRegistration(true);
        settings.setMaintenanceMode(false);
        platformSettingsRepository.save(settings);

        log.info("Platform settings seed: created the singleton settings row (supportEmail='{}')",
                defaultSupportEmail);
    }
}
