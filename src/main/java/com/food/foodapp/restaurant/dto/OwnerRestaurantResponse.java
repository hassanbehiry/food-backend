package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Builder
public class OwnerRestaurantResponse {

    private Long id;
    private String name;
    private String cuisine;
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrder;
    private LocalTime openTime;
    private LocalTime closeTime;

    @JsonProperty("isOpenForOrders")
    private boolean openForOrders;
}
