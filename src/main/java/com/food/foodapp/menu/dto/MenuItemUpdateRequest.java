package com.food.foodapp.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Full-replace of an item's editable fields, except availability — see {@link MenuItemAvailabilityRequest}. */
@Getter
@Setter
public class MenuItemUpdateRequest {

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String desc;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be >= 0")
    private BigDecimal price;

    @Size(max = 500, message = "Image reference must be at most 500 characters")
    private String img;
}
