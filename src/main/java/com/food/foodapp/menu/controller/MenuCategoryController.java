package com.food.foodapp.menu.controller;

import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.service.MenuCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing menu category (tab) listing. Thin controller — all visibility
 * rules live in {@link MenuCategoryService}.
 */
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/menu/categories")
@RequiredArgsConstructor
public class MenuCategoryController {

    private final MenuCategoryService menuCategoryService;

    /** GET /api/v1/restaurants/{restaurantId}/menu/categories — active categories, in display order. */
    @GetMapping
    public ResponseEntity<List<MenuCategoryResponse>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuCategoryService.listVisibleCategories(restaurantId));
    }
}
