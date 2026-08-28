package com.food.foodapp.settings.controller;

import com.food.foodapp.settings.dto.PlatformSettingsResponse;
import com.food.foodapp.settings.service.PlatformSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminSettingsController.class)
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformSettingsService platformSettingsService;

    @Test
    void get_returnsCurrentSettings() throws Exception {
        when(platformSettingsService.getSettings()).thenReturn(response());

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionPercentage").value(10))
                .andExpect(jsonPath("$.supportEmail").value("support@wajba.com"))
                .andExpect(jsonPath("$.maintenanceMode").value(false));
    }

    @Test
    void update_returnsUpdatedSettings() throws Exception {
        when(platformSettingsService.updateSettings(any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commissionPercentage": 10,
                                  "defaultDeliveryFee": 15,
                                  "supportEmail": "support@wajba.com",
                                  "allowRestaurantRegistration": true,
                                  "maintenanceMode": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionPercentage").value(10));
    }

    @Test
    void update_returns400_whenCommissionPercentageOutOfRange() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commissionPercentage": 150,
                                  "defaultDeliveryFee": 15,
                                  "supportEmail": "support@wajba.com",
                                  "allowRestaurantRegistration": true,
                                  "maintenanceMode": false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns400_whenSupportEmailInvalid() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commissionPercentage": 10,
                                  "defaultDeliveryFee": 15,
                                  "supportEmail": "not-an-email",
                                  "allowRestaurantRegistration": true,
                                  "maintenanceMode": false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns400_whenRequiredFieldMissing() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commissionPercentage": 10,
                                  "defaultDeliveryFee": 15,
                                  "supportEmail": "support@wajba.com",
                                  "allowRestaurantRegistration": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private PlatformSettingsResponse response() {
        return PlatformSettingsResponse.builder()
                .commissionPercentage(BigDecimal.TEN)
                .defaultDeliveryFee(BigDecimal.valueOf(15))
                .supportEmail("support@wajba.com")
                .allowRestaurantRegistration(true)
                .maintenanceMode(false)
                .build();
    }
}
