package com.food.foodapp.menu.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** The whole tabbed menu for a restaurant in one payload — {@code GET .../menu}. */
@Getter
@Builder
public class RestaurantMenuResponse {

    private List<String> tabs;
    private List<MenuItemResponse> items;
}
