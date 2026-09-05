package com.food.foodapp.menu.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Partial update of an item's editable fields (availability has its own endpoint —
 * {@link MenuItemAvailabilityRequest}). Any field left out is unchanged; in particular a missing
 * {@code img} does NOT clear the existing image (send {@code ""} to clear it). The category may
 * be changed via {@code categoryId} or {@code categoryName}/{@code category}; supplying both is a
 * 400. The image URL may be sent as {@code img} or {@code image}.
 */
@Getter
@Setter
public class MenuItemUpdateRequest {

    private Long categoryId;

    @JsonAlias("category")
    @Size(max = 100, message = "Category name must be at most 100 characters")
    private String categoryName;

    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String desc;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be >= 0")
    private BigDecimal price;

    @JsonAlias("image")
    @Size(max = 500, message = "Image reference must be at most 500 characters")
    private String img;

    @AssertTrue(message = "Provide at most one of: categoryId, or a categoryName")
    public boolean isAtMostOneCategoryReference() {
        boolean hasId = categoryId != null;
        boolean hasName = categoryName != null && !categoryName.isBlank();
        return !(hasId && hasName);
    }
}
