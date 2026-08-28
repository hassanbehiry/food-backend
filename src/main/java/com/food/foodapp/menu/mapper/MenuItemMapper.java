package com.food.foodapp.menu.mapper;

import com.food.foodapp.menu.dto.MenuItemResponse;
import com.food.foodapp.menu.dto.OwnerMenuItemResponse;
import com.food.foodapp.menu.entity.MenuItem;

public final class MenuItemMapper {

    private MenuItemMapper() {
    }

    /**
     * @param tab the owning category's name, passed explicitly so callers control
     *            whether it comes from an already fetch-joined association or a
     *            category already held in hand — never trigger a lazy load here.
     */
    public static MenuItemResponse toResponse(MenuItem item, String tab) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .desc(item.getDescription())
                .price(item.getPrice())
                .img(item.getImageUrl())
                .available(item.isAvailable())
                .tab(tab)
                .build();
    }

    public static OwnerMenuItemResponse toOwnerResponse(MenuItem item) {
        return OwnerMenuItemResponse.builder()
                .id(item.getId())
                .categoryId(item.getCategory().getId())
                .name(item.getName())
                .desc(item.getDescription())
                .price(item.getPrice())
                .img(item.getImageUrl())
                .available(item.isAvailable())
                .displayOrder(item.getDisplayOrder())
                .build();
    }
}
