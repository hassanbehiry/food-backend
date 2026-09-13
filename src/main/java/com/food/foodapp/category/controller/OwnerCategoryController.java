package com.food.foodapp.category.controller;

import com.food.foodapp.category.dto.CategoryCreateRequest;
import com.food.foodapp.category.dto.CategoryResponse;
import com.food.foodapp.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an authenticated owner add a new platform-wide category (homepage discovery chip) when the
 * one they need does not exist yet, so it becomes selectable from the restaurant settings category
 * picker. Distinct from {@link CategoryController}, which only exposes the public read-only list.
 * <p>
 * Authorization: {@code /api/v1/owner/**} requires authentication at the filter chain (see
 * {@code SecurityConfig}) — creating a category is not restaurant-scoped, so no ownership check
 * applies beyond that.
 */
@RestController
@RequestMapping("/api/v1/owner/categories")
@RequiredArgsConstructor
public class OwnerCategoryController {

    private final CategoryService categoryService;

    /** POST /api/v1/owner/categories */
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }
}
