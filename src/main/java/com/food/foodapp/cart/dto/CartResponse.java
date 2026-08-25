package com.food.foodapp.cart.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** The authoritative server cart — every field here is computed server-side; nothing is echoed from a request. */
@Getter
@Builder
public class CartResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private BigDecimal total;
}
