package com.food.foodapp.menu.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MenuCategoryReorderRequest {

    /** The full, ordered set of category ids for the restaurant — position in the list becomes displayOrder. */
    @NotEmpty(message = "categoryIds is required")
    private List<@NotNull Long> categoryIds;
}
