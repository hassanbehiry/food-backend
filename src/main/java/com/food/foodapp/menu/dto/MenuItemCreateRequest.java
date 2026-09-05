package com.food.foodapp.menu.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The category may be given either as {@code categoryId} (an existing menu category of this
 * restaurant) or as {@code categoryName} / {@code category} (a name — resolved to an existing
 * category of this restaurant, or created if none matches). Exactly one must be supplied. The
 * image URL may be sent as {@code img} or {@code image}.
 */
@Getter
@Setter
public class MenuItemCreateRequest {

    private Long categoryId;

    @JsonAlias("category")
    @Size(max = 100, message = "Category name must be at most 100 characters")
    private String categoryName;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String desc;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be >= 0")
    private BigDecimal price;

    @JsonAlias("image")
    @Size(max = 500, message = "Image reference must be at most 500 characters")
    private String img;

    @AssertTrue(message = "Provide exactly one of: categoryId, or a categoryName")
    public boolean isExactlyOneCategoryReference() {
        boolean hasId = categoryId != null;
        boolean hasName = categoryName != null && !categoryName.isBlank();
        return hasId ^ hasName;
    }
}
