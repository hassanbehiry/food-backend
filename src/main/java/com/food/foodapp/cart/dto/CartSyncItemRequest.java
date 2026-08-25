package com.food.foodapp.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Mirrors the frontend's sync payload shape exactly: {@code { menuItemId, qty }} — id and quantity only. */
@Getter
@Setter
public class CartSyncItemRequest {

    @NotNull(message = "menuItemId is required")
    private Long menuItemId;

    @NotNull(message = "qty is required")
    @Min(value = 1, message = "qty must be at least 1")
    private Integer qty;
}
