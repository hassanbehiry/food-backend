package com.food.foodapp.cart.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.food.foodapp.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Mirrors the frontend's sync payload shape: {@code { id, qty }} — id and quantity only.
 * <p>
 * {@code menuItemId} is the canonical field name, but the frontend's {@code CartContext} spreads
 * its cart objects verbatim and their identifier key is {@code id}, so {@code @JsonAlias("id")}
 * lets {@code {"id": 42, "qty": 2}} bind without the frontend having to rename anything. The
 * frontend's menu ids are the real numeric {@code menu_items} primary keys (see the V2 seed), so
 * no string-id resolution is needed.
 */
@Getter
@Setter
public class CartSyncItemRequest {

    @NotNull(message = "menuItemId is required")
    @JsonAlias("id")
    private Long menuItemId;

    // @Max(50) is deliberately kept: the frontend cart stepper has no reason to exceed this, and it
    // is a sound per-line anti-abuse ceiling shared with CartItem.MAX_QUANTITY_PER_ITEM and the
    // cart_items CHECK constraint. Revisit only if a real bulk-order use case appears.
    @NotNull(message = "qty is required")
    @Min(value = 1, message = "qty must be at least 1")
    @Max(value = CartItem.MAX_QUANTITY_PER_ITEM, message = "qty must be at most " + CartItem.MAX_QUANTITY_PER_ITEM)
    private Integer qty;
}
