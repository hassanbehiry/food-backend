package com.food.foodapp.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CartSyncRequest {

    @NotNull(message = "items is required")
    @Valid
    private List<CartSyncItemRequest> items;
}
