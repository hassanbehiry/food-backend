package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Field names deliberately mirror {@code CartItemResponse} ({@code name}/{@code img}/{@code price}/{@code quantity}) so cart-to-order DTO mapping stays predictable for the frontend. */
@Getter
@Builder
public class OrderItemResponse {

    private Long id;
    private Long menuItemId;
    private String name;
    private String img;
    private BigDecimal price;
    private int quantity;
    private BigDecimal lineTotal;
}
