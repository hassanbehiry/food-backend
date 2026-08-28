package com.food.foodapp.category.mapper;

import com.food.foodapp.category.dto.CategoryResponse;
import com.food.foodapp.category.entity.Category;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(category.getIcon())
                .build();
    }
}
