package com.food.foodapp.favorite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FavoriteToggleResponse {

    @JsonProperty("isFavorite")
    private boolean favorite;
}
