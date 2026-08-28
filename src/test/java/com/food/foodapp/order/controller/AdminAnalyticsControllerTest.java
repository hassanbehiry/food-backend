package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.order.dto.AdminAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.AdminOrdersAnalyticsResponse;
import com.food.foodapp.order.dto.AdminOrdersByCityResponse;
import com.food.foodapp.order.dto.AdminRevenueAnalyticsResponse;
import com.food.foodapp.order.dto.CityOrderShareResponse;
import com.food.foodapp.order.dto.DailyOrderCountResponse;
import com.food.foodapp.order.dto.DailyRevenueResponse;
import com.food.foodapp.order.service.AdminAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminAnalyticsController.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAnalyticsService adminAnalyticsService;

    @Test
    void getOverview_returns200_withKpisAndTrends() throws Exception {
        AdminAnalyticsOverviewResponse response = AdminAnalyticsOverviewResponse.builder()
                .period("7d").periodStart(LocalDate.of(2026, 8, 20)).periodEnd(LocalDate.of(2026, 8, 26))
                .totalOrders(20).totalOrdersTrendPercentage(BigDecimal.valueOf(33.33))
                .totalRevenue(BigDecimal.valueOf(500)).totalRevenueTrendPercentage(BigDecimal.valueOf(25))
                .activeRestaurants(12).activeRestaurantsTrendPercentage(BigDecimal.valueOf(20))
                .registeredCustomers(100).registeredCustomersTrendPercentage(BigDecimal.valueOf(11.11))
                .averageOrderValue(BigDecimal.valueOf(50))
                .ordersByStatus(List.of())
                .build();
        when(adminAnalyticsService.getOverview("7d")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics/overview").param("period", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7d"))
                .andExpect(jsonPath("$.totalOrders").value(20))
                .andExpect(jsonPath("$.totalRevenue").value(500))
                .andExpect(jsonPath("$.activeRestaurants").value(12))
                .andExpect(jsonPath("$.registeredCustomers").value(100))
                .andExpect(jsonPath("$.averageOrderValue").value(50));
    }

    @Test
    void getOverview_defaultsPeriod_whenOmitted() throws Exception {
        when(adminAnalyticsService.getOverview(isNull())).thenReturn(AdminAnalyticsOverviewResponse.builder()
                .period("7d").totalOrders(0).ordersByStatus(List.of()).build());

        mockMvc.perform(get("/api/v1/admin/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7d"));
    }

    @Test
    void getOverview_returns400_forInvalidPeriod() throws Exception {
        when(adminAnalyticsService.getOverview("90d"))
                .thenThrow(new InvalidRequestParameterException("Invalid 'period' value: '90d'"));

        mockMvc.perform(get("/api/v1/admin/analytics/overview").param("period", "90d"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrders_returns200_withDailyOrdersAndChangeBadge_whenNoRangeGiven() throws Exception {
        AdminOrdersAnalyticsResponse response = AdminOrdersAnalyticsResponse.builder()
                .from(LocalDate.of(2026, 8, 24)).to(LocalDate.of(2026, 8, 30))
                .totalOrders(3).changePercentage(BigDecimal.valueOf(200))
                .dailyOrders(List.of(DailyOrderCountResponse.builder()
                        .date(LocalDate.of(2026, 8, 24)).orderCount(2).build()))
                .build();
        when(adminAnalyticsService.getOrders(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.changePercentage").value(200))
                .andExpect(jsonPath("$.dailyOrders[0].orderCount").value(2));
    }

    @Test
    void getOrders_passesFromAndToThrough_whenGiven() throws Exception {
        when(adminAnalyticsService.getOrders(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7)))
                .thenReturn(AdminOrdersAnalyticsResponse.builder()
                        .from(LocalDate.of(2026, 8, 1)).to(LocalDate.of(2026, 8, 7))
                        .totalOrders(0).dailyOrders(List.of()).build());

        mockMvc.perform(get("/api/v1/admin/analytics/orders")
                        .param("from", "2026-08-01").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-01"))
                .andExpect(jsonPath("$.to").value("2026-08-07"));
    }

    @Test
    void getOrders_returns400_whenOnlyFromGiven() throws Exception {
        when(adminAnalyticsService.getOrders(eq(LocalDate.of(2026, 8, 1)), isNull()))
                .thenThrow(new InvalidRequestParameterException(
                        "Query parameters 'from' and 'to' must both be given, or both omitted"));

        mockMvc.perform(get("/api/v1/admin/analytics/orders").param("from", "2026-08-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRevenue_returns200_withDailyRevenue() throws Exception {
        AdminRevenueAnalyticsResponse response = AdminRevenueAnalyticsResponse.builder()
                .from(LocalDate.of(2026, 8, 24)).to(LocalDate.of(2026, 8, 30))
                .totalRevenue(BigDecimal.valueOf(150)).previousPeriodRevenue(BigDecimal.valueOf(100))
                .changePercentage(BigDecimal.valueOf(50))
                .dailyRevenue(List.of(DailyRevenueResponse.builder()
                        .date(LocalDate.of(2026, 8, 24)).revenue(BigDecimal.valueOf(150)).orderCount(2).build()))
                .build();
        when(adminAnalyticsService.getRevenue(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(150))
                .andExpect(jsonPath("$.changePercentage").value(50));
    }

    @Test
    void getOrdersByCity_returns200_withCityShares() throws Exception {
        AdminOrdersByCityResponse response = AdminOrdersByCityResponse.builder()
                .from(LocalDate.of(2026, 8, 24)).to(LocalDate.of(2026, 8, 30))
                .totalOrders(100)
                .cities(List.of(
                        CityOrderShareResponse.builder().city("Cairo").orderCount(60).percentage(BigDecimal.valueOf(60)).build(),
                        CityOrderShareResponse.builder().city("Other").orderCount(40).percentage(BigDecimal.valueOf(40)).build()))
                .build();
        when(adminAnalyticsService.getOrdersByCity(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics/orders-by-city"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(100))
                .andExpect(jsonPath("$.cities[0].city").value("Cairo"))
                .andExpect(jsonPath("$.cities[0].percentage").value(60))
                .andExpect(jsonPath("$.cities[1].city").value("Other"));
    }
}
