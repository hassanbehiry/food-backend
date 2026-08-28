package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.order.dto.DailyRevenueResponse;
import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.service.OrderAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OwnerAnalyticsController.class)
class OwnerAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderAnalyticsService orderAnalyticsService;

    @Test
    void getOverview_returns200_withKpisAndTrends() throws Exception {
        OwnerAnalyticsOverviewResponse response = OwnerAnalyticsOverviewResponse.builder()
                .restaurantId(5L).restaurantName("Pizza Place")
                .periodStart(LocalDate.of(2026, 8, 1)).periodEnd(LocalDate.of(2026, 8, 26))
                .totalOrders(20).totalOrdersTrendPercentage(BigDecimal.valueOf(33.33))
                .revenue(BigDecimal.valueOf(500)).revenueTrendPercentage(BigDecimal.valueOf(25))
                .completedOrders(10).averageOrderValue(BigDecimal.valueOf(50))
                .ordersByStatus(List.of())
                .build();
        when(orderAnalyticsService.getOverview(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/owner/restaurants/5/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Pizza Place"))
                .andExpect(jsonPath("$.totalOrders").value(20))
                .andExpect(jsonPath("$.totalOrdersTrendPercentage").value(33.33))
                .andExpect(jsonPath("$.revenue").value(500))
                .andExpect(jsonPath("$.completedOrders").value(10))
                .andExpect(jsonPath("$.averageOrderValue").value(50));
    }

    @Test
    void getOverview_returns404_whenRestaurantDoesNotExist() throws Exception {
        when(orderAnalyticsService.getOverview(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/owner/restaurants/99/analytics/overview"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRevenue_returns200_withDailyRevenueAndChangeBadge_whenNoRangeGiven() throws Exception {
        OwnerRevenueAnalyticsResponse response = OwnerRevenueAnalyticsResponse.builder()
                .restaurantId(5L).from(LocalDate.of(2026, 8, 22)).to(LocalDate.of(2026, 8, 28))
                .totalRevenue(BigDecimal.valueOf(150)).previousPeriodRevenue(BigDecimal.valueOf(100))
                .changePercentage(BigDecimal.valueOf(50))
                .dailyRevenue(List.of(DailyRevenueResponse.builder()
                        .date(LocalDate.of(2026, 8, 22)).revenue(BigDecimal.valueOf(150)).orderCount(2).build()))
                .build();
        when(orderAnalyticsService.getRevenue(5L, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/owner/restaurants/5/analytics/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(150))
                .andExpect(jsonPath("$.changePercentage").value(50))
                .andExpect(jsonPath("$.dailyRevenue[0].orderCount").value(2));
    }

    @Test
    void getRevenue_passesFromAndToThrough_whenGiven() throws Exception {
        when(orderAnalyticsService.getRevenue(5L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7)))
                .thenReturn(OwnerRevenueAnalyticsResponse.builder()
                        .restaurantId(5L).from(LocalDate.of(2026, 8, 1)).to(LocalDate.of(2026, 8, 7))
                        .totalRevenue(BigDecimal.ZERO).previousPeriodRevenue(BigDecimal.ZERO)
                        .dailyRevenue(List.of()).build());

        mockMvc.perform(get("/api/v1/owner/restaurants/5/analytics/revenue")
                        .param("from", "2026-08-01").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-01"))
                .andExpect(jsonPath("$.to").value("2026-08-07"));
    }

    @Test
    void getRevenue_returns400_whenOnlyFromGiven() throws Exception {
        when(orderAnalyticsService.getRevenue(5L, LocalDate.of(2026, 8, 1), null))
                .thenThrow(new InvalidRequestParameterException(
                        "Query parameters 'from' and 'to' must both be given, or both omitted"));

        mockMvc.perform(get("/api/v1/owner/restaurants/5/analytics/revenue").param("from", "2026-08-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRevenue_returns404_whenRestaurantDoesNotExist() throws Exception {
        when(orderAnalyticsService.getRevenue(99L, null, null))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/owner/restaurants/99/analytics/revenue"))
                .andExpect(status().isNotFound());
    }
}
