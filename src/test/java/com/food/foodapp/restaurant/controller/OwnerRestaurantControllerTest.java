package com.food.foodapp.restaurant.controller;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.OwnerAccessDeniedException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.dto.OwnerRestaurantResponse;
import com.food.foodapp.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OwnerRestaurantController.class)
class OwnerRestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @Test
    void get_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(restaurantService.getOwnerRestaurant(2L))
                .thenThrow(new OwnerAccessDeniedException("You do not have permission to manage restaurant 2"));

        mockMvc.perform(get("/api/v1/owner/restaurants/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_returnsOwnerRestaurant() throws Exception {
        when(restaurantService.getOwnerRestaurant(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/owner/restaurants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Restaurant"))
                .andExpect(jsonPath("$.isOpenForOrders").value(true));
    }

    @Test
    void get_returns404_whenMissing() throws Exception {
        when(restaurantService.getOwnerRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/owner/restaurants/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSettings_returnsUpdatedRestaurant() throws Exception {
        when(restaurantService.updateSettings(eq(1L), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/owner/restaurants/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Restaurant\",\"cuisine\":\"إيطالي\","
                                + "\"deliveryFee\":15,\"minimumOrder\":50,"
                                + "\"openTime\":\"09:00:00\",\"closeTime\":\"23:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Restaurant"));
    }

    @Test
    void updateSettings_returns400_whenNameMissing() throws Exception {
        mockMvc.perform(put("/api/v1/owner/restaurants/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuisine\":\"إيطالي\",\"deliveryFee\":15,\"minimumOrder\":50,"
                                + "\"openTime\":\"09:00:00\",\"closeTime\":\"23:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSettings_returns400_whenCloseTimeNotAfterOpenTime() throws Exception {
        when(restaurantService.updateSettings(eq(1L), any()))
                .thenThrow(new InvalidRequestParameterException("closeTime must be after openTime"));

        mockMvc.perform(put("/api/v1/owner/restaurants/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Restaurant\",\"cuisine\":\"إيطالي\","
                                + "\"deliveryFee\":15,\"minimumOrder\":50,"
                                + "\"openTime\":\"12:00:00\",\"closeTime\":\"12:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSettings_returns404_whenMissing() throws Exception {
        when(restaurantService.updateSettings(eq(99L), any()))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(put("/api/v1/owner/restaurants/99/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Restaurant\",\"cuisine\":\"إيطالي\","
                                + "\"deliveryFee\":15,\"minimumOrder\":50,"
                                + "\"openTime\":\"09:00:00\",\"closeTime\":\"23:00:00\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAvailability_returnsUpdatedRestaurant() throws Exception {
        when(restaurantService.updateAvailability(eq(1L), any())).thenReturn(
                OwnerRestaurantResponse.builder().id(1L).name("Test Restaurant").cuisine("إيطالي")
                        .deliveryFee(BigDecimal.valueOf(15)).minimumOrder(BigDecimal.valueOf(50))
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(23, 0))
                        .openForOrders(false).build());

        mockMvc.perform(patch("/api/v1/owner/restaurants/1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isOpenForOrders\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOpenForOrders").value(false));
    }

    @Test
    void updateAvailability_returns400_whenFlagMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/owner/restaurants/1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAvailability_returns404_whenMissing() throws Exception {
        when(restaurantService.updateAvailability(eq(99L), any()))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(patch("/api/v1/owner/restaurants/99/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isOpenForOrders\":false}"))
                .andExpect(status().isNotFound());
    }

    private OwnerRestaurantResponse response() {
        return OwnerRestaurantResponse.builder()
                .id(1L)
                .name("Test Restaurant")
                .cuisine("إيطالي")
                .deliveryFee(BigDecimal.valueOf(15))
                .minimumOrder(BigDecimal.valueOf(50))
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(23, 0))
                .openForOrders(true)
                .build();
    }
}
