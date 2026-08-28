package com.food.foodapp.menu.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Customer-facing item shape. Groups by category name ({@code tab}), not id — the
 * frontend menu page matches items to tabs by this string, mirroring
 * {@code RestaurantMenuResponse.tabs}.
 */
@Getter
@Builder
public class MenuItemResponse {

    private Long id;
    private String name;
    private String desc;
    private BigDecimal price;
    private String img;
    private boolean available;
    private String tab;
}
