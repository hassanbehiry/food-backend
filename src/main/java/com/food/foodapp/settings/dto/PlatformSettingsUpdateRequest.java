package com.food.foodapp.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Full-replace of platform settings — every field is required, matching the admin settings form's single "Save" action. */
@Getter
@Setter
public class PlatformSettingsUpdateRequest {

    @NotNull(message = "Commission percentage is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Commission percentage must be >= 0")
    @DecimalMax(value = "100.0", inclusive = true, message = "Commission percentage must be <= 100")
    private BigDecimal commissionPercentage;

    @NotNull(message = "Default delivery fee is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Default delivery fee must be >= 0")
    private BigDecimal defaultDeliveryFee;

    @NotBlank(message = "Support email is required")
    @Email(message = "Support email must be valid")
    private String supportEmail;

    @NotNull(message = "allowRestaurantRegistration is required")
    private Boolean allowRestaurantRegistration;

    @NotNull(message = "maintenanceMode is required")
    private Boolean maintenanceMode;
}
