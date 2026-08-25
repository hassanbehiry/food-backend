package com.food.foodapp.cart.controller;

import com.food.foodapp.cart.dto.CartAddItemRequest;
import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.dto.CartSyncRequest;
import com.food.foodapp.cart.dto.CartUpdateItemRequest;
import com.food.foodapp.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer cart endpoints. Thin controller — no method here needs to know who the
 * caller is; {@link CartService} resolves that itself via {@code UserContext}.
 * {@code GET} and {@code POST /sync} are the two the frontend's {@code cartService.js}
 * already calls by name; the item-level endpoints below them are the underlying
 * operations sync is built from.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** GET /api/v1/cart */
    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        return ResponseEntity.ok(cartService.getCart());
    }

    /** POST /api/v1/cart/sync */
    @PostMapping("/sync")
    public ResponseEntity<CartResponse> sync(@Valid @RequestBody CartSyncRequest request) {
        return ResponseEntity.ok(cartService.syncCart(request));
    }

    /** POST /api/v1/cart/items */
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody CartAddItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(request));
    }

    /** PATCH /api/v1/cart/items/{cartItemId} — directly sets the item's quantity. */
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable Long cartItemId, @Valid @RequestBody CartUpdateItemRequest request) {
        return ResponseEntity.ok(cartService.updateItemQuantity(cartItemId, request));
    }

    /** DELETE /api/v1/cart/items/{cartItemId} */
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable Long cartItemId) {
        return ResponseEntity.ok(cartService.removeItem(cartItemId));
    }

    /** DELETE /api/v1/cart */
    @DeleteMapping
    public ResponseEntity<CartResponse> clearCart() {
        return ResponseEntity.ok(cartService.clearCart());
    }
}
