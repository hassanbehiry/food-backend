package com.food.foodapp.menu.controller;

import com.food.foodapp.menu.dto.MenuItemResponse;
import com.food.foodapp.menu.dto.RestaurantMenuResponse;
import com.food.foodapp.menu.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer-facing restaurant menu retrieval. Thin controller — all visibility rules
 * live in {@link MenuItemService}.
 */
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuItemService menuItemService;

    /** GET /api/v1/restaurants/{restaurantId}/menu — the whole tabbed menu (tabs + items) in one call. */
    @GetMapping
    public ResponseEntity<RestaurantMenuResponse> getMenu(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuItemService.getMenu(restaurantId));
    }

    /** GET /api/v1/restaurants/{restaurantId}/menu/items?categoryId=... — all visible items, optionally filtered to one category. */
    @GetMapping("/items")
    public ResponseEntity<List<MenuItemResponse>> listItems(
            @PathVariable Long restaurantId, @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(menuItemService.listVisibleItems(restaurantId, categoryId));
    }
}
