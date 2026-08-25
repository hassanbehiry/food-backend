package com.food.foodapp.cart.dto;

import com.food.foodapp.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartAddItemRequest {

    @NotNull(message = "menuItemId is required")
    private Long menuItemId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = CartItem.MAX_QUANTITY_PER_ITEM, message = "quantity must be at most " + CartItem.MAX_QUANTITY_PER_ITEM)
    private Integer quantity;
}
