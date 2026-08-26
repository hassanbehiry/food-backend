package com.food.foodapp.favorite.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoriteToggleRequest {

    @NotNull(message = "restaurantId is required")
    private Long restaurantId;
}
