package com.food.foodapp.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Adds a new platform-wide category (homepage discovery chip) that any restaurant, including the
 * caller's own, can then be linked to. The slug is derived from {@code name} server-side, not
 * accepted from the client — see {@link com.food.foodapp.category.service.CategoryService}.
 */
@Getter
@Setter
public class CategoryCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    /**
     * Either a FontAwesome solid-style class token (e.g. {@code fa-pizza-slice}) or an
     * {@code http(s)://} image URL. Defaults to a generic icon when omitted.
     */
    @Size(max = 500, message = "Icon must be at most 500 characters")
    private String icon;
}
