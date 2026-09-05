package com.food.foodapp.menu.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Owner-facing item shape — carries {@code categoryId} so an edit form can preselect the category
 * dropdown, and {@code tab} (the category's display name) which the owner menu table renders.
 */
@Getter
@Builder
public class OwnerMenuItemResponse {

    private Long id;
    private Long categoryId;
    private String tab;
    private String name;
    private String desc;
    private BigDecimal price;
    private String img;
    private boolean available;
    private int displayOrder;
}
