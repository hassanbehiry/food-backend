package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class AdminRestaurantResponse {

    private Long id;
    private String name;
    private String cuisine;
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrder;
    private LocalTime openTime;
    private LocalTime closeTime;

    @JsonProperty("isOpenForOrders")
    private boolean openForOrders;

    private RestaurantApprovalStatus approvalStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
