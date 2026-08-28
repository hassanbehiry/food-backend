package com.food.foodapp.menu.mapper;

import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.entity.MenuCategory;

public final class MenuCategoryMapper {

    private MenuCategoryMapper() {
    }

    public static MenuCategoryResponse toResponse(MenuCategory category) {
        return MenuCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .active(category.isActive())
                .build();
    }
}
