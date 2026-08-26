package com.food.foodapp.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Full-replace of a restaurant's owner-editable settings, except availability — see {@link RestaurantAvailabilityRequest}. */
@Getter
@Setter
public class RestaurantSettingsUpdateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @NotBlank(message = "Cuisine is required")
    @Size(max = 150, message = "Cuisine must be at most 150 characters")
    private String cuisine;

    @NotNull(message = "Delivery fee is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Delivery fee must be >= 0")
    private BigDecimal deliveryFee;

    @NotNull(message = "Minimum order is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum order must be >= 0")
    private BigDecimal minimumOrder;

    @NotNull(message = "Opening time is required")
    private LocalTime openTime;

    @NotNull(message = "Closing time is required")
    private LocalTime closeTime;
}
