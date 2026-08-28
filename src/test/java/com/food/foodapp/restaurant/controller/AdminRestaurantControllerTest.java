package com.food.foodapp.restaurant.controller;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.InvalidRestaurantApprovalTransitionException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.dto.AdminRestaurantListResponse;
import com.food.foodapp.restaurant.dto.AdminRestaurantResponse;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminRestaurantController.class)
class AdminRestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @Test
    void list_returnsRestaurantsWithPaginationMetadata() throws Exception {
        AdminRestaurantListResponse listResponse = AdminRestaurantListResponse.builder()
                .restaurants(List.of(response()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        when(restaurantService.listRestaurantsForAdmin(any(), eq(0), eq(20))).thenReturn(listResponse);

        mockMvc.perform(get("/api/v1/admin/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants[0].name").value("Test Restaurant"))
                .andExpect(jsonPath("$.restaurants[0].approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_returns400_whenStatusIsInvalid() throws Exception {
        when(restaurantService.listRestaurantsForAdmin(eq("invalid"), eq(0), eq(20)))
                .thenThrow(new InvalidRequestParameterException("Invalid 'status' value: 'invalid'"));

        mockMvc.perform(get("/api/v1/admin/restaurants").param("status", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsRestaurant() throws Exception {
        when(restaurantService.getAdminRestaurant(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/admin/restaurants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Restaurant"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        when(restaurantService.getAdminRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(get("/api/v1/admin/restaurants/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_returnsApprovedRestaurant() throws Exception {
        when(restaurantService.approveRestaurant(1L)).thenReturn(response(RestaurantApprovalStatus.APPROVED));

        mockMvc.perform(patch("/api/v1/admin/restaurants/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void approve_returns409_whenTransitionInvalid() throws Exception {
        when(restaurantService.approveRestaurant(1L))
                .thenThrow(new InvalidRestaurantApprovalTransitionException("Restaurant 1 cannot move from APPROVED to APPROVED"));

        mockMvc.perform(patch("/api/v1/admin/restaurants/1/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    void approve_returns404_whenMissing() throws Exception {
        when(restaurantService.approveRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        mockMvc.perform(patch("/api/v1/admin/restaurants/99/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reject_returnsRejectedRestaurant() throws Exception {
        when(restaurantService.rejectRestaurant(1L)).thenReturn(response(RestaurantApprovalStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/admin/restaurants/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));
    }

    @Test
    void reject_returns409_whenTransitionInvalid() throws Exception {
        when(restaurantService.rejectRestaurant(1L))
                .thenThrow(new InvalidRestaurantApprovalTransitionException("Restaurant 1 cannot move from APPROVED to REJECTED"));

        mockMvc.perform(patch("/api/v1/admin/restaurants/1/reject"))
                .andExpect(status().isConflict());
    }

    @Test
    void suspend_returnsSuspendedRestaurant() throws Exception {
        when(restaurantService.suspendRestaurant(1L)).thenReturn(response(RestaurantApprovalStatus.SUSPENDED));

        mockMvc.perform(patch("/api/v1/admin/restaurants/1/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("SUSPENDED"));
    }

    @Test
    void suspend_returns409_whenTransitionInvalid() throws Exception {
        when(restaurantService.suspendRestaurant(1L))
                .thenThrow(new InvalidRestaurantApprovalTransitionException("Restaurant 1 cannot move from PENDING to SUSPENDED"));

        mockMvc.perform(patch("/api/v1/admin/restaurants/1/suspend"))
                .andExpect(status().isConflict());
    }

    private AdminRestaurantResponse response() {
        return response(RestaurantApprovalStatus.PENDING);
    }

    private AdminRestaurantResponse response(RestaurantApprovalStatus approvalStatus) {
        return AdminRestaurantResponse.builder()
                .id(1L)
                .name("Test Restaurant")
                .cuisine("إيطالي")
                .deliveryFee(BigDecimal.valueOf(15))
                .minimumOrder(BigDecimal.valueOf(50))
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(23, 0))
                .openForOrders(true)
                .approvalStatus(approvalStatus)
                .build();
    }
}
