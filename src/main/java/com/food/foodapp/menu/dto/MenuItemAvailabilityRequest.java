package com.food.foodapp.menu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuItemAvailabilityRequest {

    @NotNull(message = "Available flag is required")
    private Boolean available;
}
