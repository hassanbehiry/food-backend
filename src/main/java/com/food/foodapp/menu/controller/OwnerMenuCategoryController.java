package com.food.foodapp.menu.controller;

import com.food.foodapp.menu.dto.MenuCategoryCreateRequest;
import com.food.foodapp.menu.dto.MenuCategoryReorderRequest;
import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.dto.MenuCategoryUpdateRequest;
import com.food.foodapp.menu.service.MenuCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner-side menu category (tab) management: create, update, delete, reorder.
 * Thin controller — all business rules live in {@link MenuCategoryService}.
 * <p>
 * Authorization: {@code /api/v1/owner/**} requires authentication at the filter chain, and
 * {@link MenuCategoryService} calls {@code RestaurantOwnershipGuard.requireOwnedRestaurant} before
 * every mutation — an authenticated caller who is not the restaurant's owner gets {@code 403}.
 */
@RestController
@RequestMapping("/api/v1/owner/restaurants/{restaurantId}/menu/categories")
@RequiredArgsConstructor
public class OwnerMenuCategoryController {

    private final MenuCategoryService menuCategoryService;

    /** GET /api/v1/owner/restaurants/{restaurantId}/menu/categories — all categories, in display order. */
    @GetMapping
    public ResponseEntity<List<MenuCategoryResponse>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuCategoryService.listCategoriesForOwner(restaurantId));
    }

    /** POST /api/v1/owner/restaurants/{restaurantId}/menu/categories */
    @PostMapping
    public ResponseEntity<MenuCategoryResponse> create(
            @PathVariable Long restaurantId, @Valid @RequestBody MenuCategoryCreateRequest request) {
        MenuCategoryResponse created = menuCategoryService.createCategory(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/v1/owner/restaurants/{restaurantId}/menu/categories/{categoryId} */
    @PutMapping("/{categoryId}")
    public ResponseEntity<MenuCategoryResponse> update(
            @PathVariable Long restaurantId, @PathVariable Long categoryId,
            @Valid @RequestBody MenuCategoryUpdateRequest request) {
        return ResponseEntity.ok(menuCategoryService.updateCategory(restaurantId, categoryId, request));
    }

    /** DELETE /api/v1/owner/restaurants/{restaurantId}/menu/categories/{categoryId} */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Long restaurantId, @PathVariable Long categoryId) {
        menuCategoryService.deleteCategory(restaurantId, categoryId);
        return ResponseEntity.noContent().build();
    }

    /** PUT /api/v1/owner/restaurants/{restaurantId}/menu/categories/reorder */
    @PutMapping("/reorder")
    public ResponseEntity<List<MenuCategoryResponse>> reorder(
            @PathVariable Long restaurantId, @Valid @RequestBody MenuCategoryReorderRequest request) {
        return ResponseEntity.ok(menuCategoryService.reorderCategories(restaurantId, request));
    }
}
