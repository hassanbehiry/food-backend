package com.food.foodapp.cart.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CartItemResponse {

    private Long id;
    private Long menuItemId;
    private String name;
    private String img;
    private BigDecimal price;
    private int quantity;
    private BigDecimal lineTotal;
}
