package com.food.foodapp.order.controller;

import com.food.foodapp.common.exception.OwnerAccessDeniedException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderStatsResponse;
import com.food.foodapp.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    void getDashboard_returns200_withStatsAndRecentOrders() throws Exception {
        OwnerDashboardResponse response = OwnerDashboardResponse.builder()
                .restaurantId(5L)
                .restaurantName("Pizza Place")
                .stats(OwnerOrderStatsResponse.builder()
                        .newCount(3).preparingCount(2).onTheWayCount(1).deliveredCount(10).totalCount(16)
                        .build())
                .recentOrders(List.of())
                .build();
        when(orderService.getDashboard(5L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/owner/dashboard/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Pizza Place"))
                .andExpect(jsonPath("$.stats.newCount").value(3))
                .andExpect(jsonPath("$.stats.totalCount").value(16));
    }

    @Test
    void getDashboard_returns404_whenRestaurantDoesNotExist() throws Exception {
        when(orderService.getDashboard(99L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/owner/dashboard/99"))
                .andExpect(status().isNotFound());
    }
}
