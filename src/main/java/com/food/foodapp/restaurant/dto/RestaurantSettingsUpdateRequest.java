package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
 * {@code coverImageUrl} is the storefront hero image shown at the top of the restaurant page;
 * {@code logoUrl} is the small restaurant logo shown in listings and the storefront header.
 * {@code openTime} and {@code closeTime} must be sent together or not at all. These hours are
 * what determine whether the restaurant currently shows as open or closed — there is no
 * separate manual toggle.
 */
@Getter
@Setter
public class RestaurantSettingsUpdateRequest {

    @JsonAlias("restName")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 150, message = "Cuisine must be at most 150 characters")
    private String cuisine;

    @Size(max = 500, message = "Cover image URL must be at most 500 characters")
    private String coverImageUrl;

    @Size(max = 500, message = "Logo URL must be at most 500 characters")
    private String logoUrl;

    @DecimalMin(value = "0.0", inclusive = true, message = "Delivery fee must be >= 0")
    private BigDecimal deliveryFee;

    @JsonAlias("minOrder")
    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum order must be >= 0")
    private BigDecimal minimumOrder;

    private LocalTime openTime;

    private LocalTime closeTime;

    /** When present, replaces the restaurant's platform category (homepage discovery chip) with this one. */
    private Long categoryId;
}
