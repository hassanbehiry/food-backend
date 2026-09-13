package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.food.foodapp.category.dto.CategoryResponse;
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
    private String logoUrl;
    private String coverImageUrl;
    private BigDecimal deliveryFee;
    private BigDecimal minimumOrder;
    private LocalTime openTime;
    private LocalTime closeTime;

    /** The restaurant's platform category (homepage discovery chip), if one has been linked. */
    private CategoryResponse category;

    @JsonProperty("isOpenForOrders")
    private boolean openForOrders;
}
