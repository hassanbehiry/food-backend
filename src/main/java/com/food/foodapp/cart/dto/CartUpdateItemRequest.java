package com.food.foodapp.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Directly sets a cart item's quantity to an absolute value — increment/decrement are just this with a computed value. */
@Getter
@Setter
public class CartUpdateItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;
}
