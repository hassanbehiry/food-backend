package com.food.foodapp.restaurant.controller;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.dto.RestaurantDetailResponse;
import com.food.foodapp.restaurant.dto.RestaurantListResponse;
import com.food.foodapp.restaurant.dto.RestaurantSummaryResponse;
import com.food.foodapp.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(RestaurantController.class)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @Test
    void list_returnsRestaurantsWithPaginationMetadata() throws Exception {
        RestaurantSummaryResponse summary = summary();
        RestaurantListResponse listResponse = RestaurantListResponse.builder()
                .restaurants(List.of(summary))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        when(restaurantService.searchRestaurants(any(), any(), any(), eq(0), eq(20))).thenReturn(listResponse);

        mockMvc.perform(get("/api/v1/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants[0].name").value("Test Restaurant"))
                .andExpect(jsonPath("$.restaurants[0].isOpenForOrders").value(true))
                .andExpect(jsonPath("$.restaurants[0].categoryIds[0]").value("pizza"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_returns400_whenSortIsInvalid() throws Exception {
        when(restaurantService.searchRestaurants(any(), any(), eq("invalid"), eq(0), eq(20)))
                .thenThrow(new InvalidRequestParameterException("Invalid 'sort' value: 'invalid'"));

        mockMvc.perform(get("/api/v1/restaurants").param("sort", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void list_acceptsCategorySlug_andPassesItThrough() throws Exception {
        RestaurantListResponse listResponse = RestaurantListResponse.builder()
                .restaurants(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .build();
        when(restaurantService.searchRestaurants(any(), eq("pizza"), any(), eq(0), eq(20)))
                .thenReturn(listResponse);

        mockMvc.perform(get("/api/v1/restaurants").param("category", "pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getById_returnsDetail_whenFound() throws Exception {
        RestaurantDetailResponse detail = RestaurantDetailResponse.builder()
                .id(1L)
                .name("Test Restaurant")
                .cuisine("إيطالي")
                .ratingAverage(BigDecimal.valueOf(4.5))
                .reviewCount(10)
                .deliveryFee(BigDecimal.valueOf(15))
                .minimumOrder(BigDecimal.valueOf(50))
                .estimatedDeliveryMinMinutes(25)
                .estimatedDeliveryMaxMinutes(35)
                .estimatedDeliveryLabel("25-35 دقيقة")
                .openForOrders(true)
                .categoryIds(List.of("pizza"))
                .categories(List.of())
                .build();
        when(restaurantService.getVisibleRestaurantById(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/restaurants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isOpenForOrders").value(true))
                .andExpect(jsonPath("$.categoryIds[0]").value("pizza"));
    }

    @Test
    void getById_returns404_whenNotVisible() throws Exception {
        when(restaurantService.getVisibleRestaurantById(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/restaurants/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getById_returns400_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/abc"))
                .andExpect(status().isBadRequest());
    }

    private RestaurantSummaryResponse summary() {
        return RestaurantSummaryResponse.builder()
                .id(1L)
                .name("Test Restaurant")
                .cuisine("إيطالي")
                .ratingAverage(BigDecimal.valueOf(4.5))
                .reviewCount(10)
                .deliveryFee(BigDecimal.valueOf(15))
                .minimumOrder(BigDecimal.valueOf(50))
                .estimatedDeliveryMinMinutes(25)
                .estimatedDeliveryMaxMinutes(35)
                .estimatedDeliveryLabel("25-35 دقيقة")
                .categoryIds(List.of("pizza"))
                .openForOrders(true)
                .build();
    }
}
