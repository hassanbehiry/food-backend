package com.food.foodapp.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantAvailabilityRequest {

    @JsonProperty("isOpenForOrders")
    @NotNull(message = "isOpenForOrders flag is required")
    private Boolean openForOrders;
}
