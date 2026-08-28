package com.food.foodapp.menu.controller;

import com.food.foodapp.menu.dto.MenuItemAvailabilityRequest;
import com.food.foodapp.menu.dto.MenuItemCreateRequest;
import com.food.foodapp.menu.dto.MenuItemUpdateRequest;
import com.food.foodapp.menu.dto.OwnerMenuItemResponse;
import com.food.foodapp.menu.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner-side menu item management: create, update, delete, availability toggle.
 * Thin controller — all business rules live in {@link MenuItemService}.
 * <p>
 * Paths sit one level shallower than {@code /menu/items} to match the frontend's
 * {@code adminService.js}, which already calls these concrete owner-side paths.
 * <p>
 * NOTE: same authorization gap as {@link OwnerMenuCategoryController} — this
 * codebase has no owner-authentication middleware yet, so these endpoints are only
 * scoped to "the item belongs to this restaurantId", not to the authenticated owner.
 */
@RestController
@RequestMapping("/api/v1/owner/restaurants/{restaurantId}/items")
@RequiredArgsConstructor
public class OwnerMenuItemController {

    private final MenuItemService menuItemService;

    /** GET /api/v1/owner/restaurants/{restaurantId}/items — all items, any category visibility. */
    @GetMapping
    public ResponseEntity<List<OwnerMenuItemResponse>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuItemService.listItemsForOwner(restaurantId));
    }

    /** POST /api/v1/owner/restaurants/{restaurantId}/items */
    @PostMapping
    public ResponseEntity<OwnerMenuItemResponse> create(
            @PathVariable Long restaurantId, @Valid @RequestBody MenuItemCreateRequest request) {
        OwnerMenuItemResponse created = menuItemService.createItem(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/v1/owner/restaurants/{restaurantId}/items/{itemId} */
    @PutMapping("/{itemId}")
    public ResponseEntity<OwnerMenuItemResponse> update(
            @PathVariable Long restaurantId, @PathVariable Long itemId,
            @Valid @RequestBody MenuItemUpdateRequest request) {
        return ResponseEntity.ok(menuItemService.updateItem(restaurantId, itemId, request));
    }

    /** DELETE /api/v1/owner/restaurants/{restaurantId}/items/{itemId} */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable Long restaurantId, @PathVariable Long itemId) {
        menuItemService.deleteItem(restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/v1/owner/restaurants/{restaurantId}/items/{itemId}/availability */
    @PatchMapping("/{itemId}/availability")
    public ResponseEntity<OwnerMenuItemResponse> updateAvailability(
            @PathVariable Long restaurantId, @PathVariable Long itemId,
            @Valid @RequestBody MenuItemAvailabilityRequest request) {
        return ResponseEntity.ok(menuItemService.updateAvailability(restaurantId, itemId, request));
    }
}
