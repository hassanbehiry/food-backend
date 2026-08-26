package com.food.foodapp.favorite.controller;

import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.dto.FavoriteToggleResponse;
import com.food.foodapp.favorite.service.FavoriteService;
import com.food.foodapp.restaurant.dto.RestaurantSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteService favoriteService;

    @Test
    void listFavorites_returnsFavorites() throws Exception {
        when(favoriteService.listFavorites()).thenReturn(List.of(favorite()));

        mockMvc.perform(get("/api/v1/user/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].restaurant.id").value(10))
                .andExpect(jsonPath("$[0].isAvailable").value(true));
    }

    @Test
    void listFavorites_returns401_whenUnauthenticated() throws Exception {
        when(favoriteService.listFavorites()).thenThrow(new UnauthenticatedException("Authentication required"));

        mockMvc.perform(get("/api/v1/user/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void toggleFavorite_returnsIsFavoriteTrue_whenNewlyFavorited() throws Exception {
        when(favoriteService.toggleFavorite(any())).thenReturn(FavoriteToggleResponse.builder().favorite(true).build());

        mockMvc.perform(post("/api/v1/user/favorites/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(true));
    }

    @Test
    void toggleFavorite_returnsIsFavoriteFalse_whenUnfavorited() throws Exception {
        when(favoriteService.toggleFavorite(any())).thenReturn(FavoriteToggleResponse.builder().favorite(false).build());

        mockMvc.perform(post("/api/v1/user/favorites/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(false));
    }

    @Test
    void toggleFavorite_returns400_whenRestaurantIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/user/favorites/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toggleFavorite_returns404_whenRestaurantDoesNotExist() throws Exception {
        when(favoriteService.toggleFavorite(any()))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 999"));

        mockMvc.perform(post("/api/v1/user/favorites/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":999}"))
                .andExpect(status().isNotFound());
    }

    private FavoriteResponse favorite() {
        RestaurantSummaryResponse restaurant = RestaurantSummaryResponse.builder()
                .id(10L)
                .name("Test Restaurant")
                .cuisine("Italian")
                .ratingAverage(BigDecimal.valueOf(4.5))
                .deliveryFee(BigDecimal.valueOf(10))
                .minimumOrder(BigDecimal.valueOf(50))
                .estimatedDeliveryMinMinutes(20)
                .estimatedDeliveryMaxMinutes(30)
                .openForOrders(true)
                .build();
        return FavoriteResponse.builder()
                .restaurant(restaurant)
                .available(true)
                .build();
    }
}
