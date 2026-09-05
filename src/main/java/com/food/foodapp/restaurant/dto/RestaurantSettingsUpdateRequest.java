package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Partial update of a restaurant's owner-editable settings. Every field is optional — a field
 * left out is unchanged — so the frontend's single "Save" can send whatever it changed.
 * {@code name} also accepts the alias {@code restName}, {@code minimumOrder} accepts {@code minOrder}.
 * {@code openTime} and {@code closeTime} must be sent together or not at all.
 * {@code isOpenForOrders}, when present, is applied here too (the same toggle the dedicated
 * {@link RestaurantAvailabilityRequest} endpoint sets).
 */
@Getter
@Setter
public class RestaurantSettingsUpdateRequest {

    @JsonAlias("restName")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 150, message = "Cuisine must be at most 150 characters")
    private String cuisine;

    @DecimalMin(value = "0.0", inclusive = true, message = "Delivery fee must be >= 0")
    private BigDecimal deliveryFee;

    @JsonAlias("minOrder")
    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum order must be >= 0")
    private BigDecimal minimumOrder;

    private LocalTime openTime;

    private LocalTime closeTime;

    @JsonProperty("isOpenForOrders")
    private Boolean openForOrders;
}
