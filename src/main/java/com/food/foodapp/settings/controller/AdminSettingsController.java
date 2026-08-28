package com.food.foodapp.settings.controller;

import com.food.foodapp.settings.dto.PlatformSettingsResponse;
import com.food.foodapp.settings.dto.PlatformSettingsUpdateRequest;
import com.food.foodapp.settings.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin settings: read and full-replace update of the single {@link
 * com.food.foodapp.settings.entity.PlatformSettings} row. Thin controller — all business rules
 * live in {@link PlatformSettingsService}.
 * <p>
 * NOTE: same authorization gap as {@link com.food.foodapp.restaurant.controller.AdminRestaurantController}
 * — this codebase has no admin-authentication middleware yet (no {@code ADMIN} role, no Spring
 * Security), so these endpoints are not yet gated to an authenticated admin.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PlatformSettingsService platformSettingsService;

    /** GET /api/v1/admin/settings */
    @GetMapping
    public ResponseEntity<PlatformSettingsResponse> get() {
        return ResponseEntity.ok(platformSettingsService.getSettings());
    }

    /** PUT /api/v1/admin/settings */
    @PutMapping
    public ResponseEntity<PlatformSettingsResponse> update(@Valid @RequestBody PlatformSettingsUpdateRequest request) {
        return ResponseEntity.ok(platformSettingsService.updateSettings(request));
    }
}
