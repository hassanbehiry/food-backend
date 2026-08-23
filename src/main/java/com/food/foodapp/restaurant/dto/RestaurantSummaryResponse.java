package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RestaurantSummaryResponse {

    private Long id;
    private String name;
    private String cuisine;
    private String logoUrl;
    private String coverImageUrl;
    private BigDecimal ratingAverage;
    private int reviewCount;
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrder;
    private int estimatedDeliveryMinMinutes;
    private int estimatedDeliveryMaxMinutes;
    private String estimatedDeliveryLabel;

    @JsonProperty("isOpenForOrders")
    private boolean openForOrders;
}
