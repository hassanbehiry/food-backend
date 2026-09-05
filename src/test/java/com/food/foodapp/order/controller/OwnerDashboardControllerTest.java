package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.OwnerAccessDeniedException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.order.dto.DailyRevenueResponse;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderStatsResponse;
import com.food.foodapp.order.dto.OwnerOrderSummaryResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.service.OrderService;
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
@WebMvcTest(OwnerDashboardController.class)
class OwnerDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void ownerEndpoint_returns403_whenCallerDoesNotOwnTheRestaurant() throws Exception {
        when(orderService.getDashboard(2L)).thenThrow(new OwnerAccessDeniedException("nope"));

        mockMvc.perform(get("/api/v1/owner/dashboard/2"))
                .andExpect(status().isForbidden());
    }


    @Test
    void getDashboard_returns200_withStatsRecentOrdersMonthKpisAndRevenueSeries() throws Exception {
        when(orderService.getDashboard(5L)).thenReturn(fullDashboard());

        mockMvc.perform(get("/api/v1/owner/dashboard/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Pizza Place"))
                .andExpect(jsonPath("$.stats.newCount").value(3))
                .andExpect(jsonPath("$.stats.totalCount").value(16))
                .andExpect(jsonPath("$.recentOrders[0].itemCount").value(4))
                .andExpect(jsonPath("$.monthOrders").value(42))
                .andExpect(jsonPath("$.monthOrdersTrendPct").value(12.5))
                .andExpect(jsonPath("$.monthRevenue").value(3200))
                .andExpect(jsonPath("$.last7DaysRevenue.length()").value(7))
                .andExpect(jsonPath("$.weekOverWeekPct").value(8));
    }

    @Test
    void getDashboard_returns404_whenRestaurantDoesNotExist() throws Exception {
        when(orderService.getDashboard(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/owner/dashboard/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noArgDashboard_returns200_forTheCallersOwnRestaurant() throws Exception {
        when(orderService.getDashboard()).thenReturn(fullDashboard());

        mockMvc.perform(get("/api/v1/owner/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(5))
                .andExpect(jsonPath("$.monthOrders").value(42))
                .andExpect(jsonPath("$.last7DaysRevenue.length()").value(7));
    }

    @Test
    void noArgDashboard_returns404_whenTheCallerOwnsNoRestaurant() throws Exception {
        when(orderService.getDashboard())
                .thenThrow(new RestaurantNotFoundException("No restaurant is associated with your account"));

        mockMvc.perform(get("/api/v1/owner/dashboard"))
                .andExpect(status().isNotFound());
    }

    private OwnerDashboardResponse fullDashboard() {
        List<DailyRevenueResponse> series = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            series.add(DailyRevenueResponse.builder()
                    .date(LocalDate.of(2026, 8, 22).plusDays(i)).revenue(BigDecimal.ZERO).orderCount(0).build());
        }
        return OwnerDashboardResponse.builder()
                .restaurantId(5L)
                .restaurantName("Pizza Place")
                .stats(OwnerOrderStatsResponse.builder()
                        .newCount(3).preparingCount(2).onTheWayCount(1).deliveredCount(10).totalCount(16)
                        .build())
                .recentOrders(List.of(OwnerOrderSummaryResponse.builder()
                        .id(700L).orderNumber("ORD-20260825-000001").customerName("Ali")
                        .itemCount(4).total(BigDecimal.valueOf(112)).status(OrderStatus.NEW).build()))
                .monthOrders(42).monthOrdersTrendPct(BigDecimal.valueOf(12.5))
                .monthRevenue(BigDecimal.valueOf(3200)).monthRevenueTrendPct(BigDecimal.valueOf(8))
                .last7DaysRevenue(series).weekOverWeekPct(BigDecimal.valueOf(8))
                .build();
    }
}
