package com.food.foodapp.menu.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Owner-facing item shape — carries {@code categoryId} so an edit form can preselect the category dropdown. */
@Getter
@Builder
public class OwnerMenuItemResponse {

    private Long id;
    private Long categoryId;
    private String name;
    private String desc;
    private BigDecimal price;
    private String img;
    private boolean available;
    private int displayOrder;
}
