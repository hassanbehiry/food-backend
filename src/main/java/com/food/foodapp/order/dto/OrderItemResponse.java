package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderItemResponse {

    private Long id;
    private Long menuItemId;
    private String name;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;
}
