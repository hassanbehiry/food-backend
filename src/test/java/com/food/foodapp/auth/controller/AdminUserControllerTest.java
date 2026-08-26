package com.food.foodapp.auth.controller;

import com.food.foodapp.auth.dto.AdminUserListResponse;
import com.food.foodapp.auth.dto.AdminUserResponse;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.service.AdminUserService;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.InvalidUserStatusTransitionException;
import com.food.foodapp.common.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @Test
    void list_returnsUsersWithPaginationMetadata() throws Exception {
        AdminUserListResponse listResponse = AdminUserListResponse.builder()
                .users(List.of(response()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
        when(adminUserService.listUsers(any(), any(), eq(0), eq(20))).thenReturn(listResponse);

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].email").value("ali@example.com"))
                .andExpect(jsonPath("$.users[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_returns400_whenRoleIsInvalid() throws Exception {
        when(adminUserService.listUsers(eq("invalid"), any(), eq(0), eq(20)))
                .thenThrow(new InvalidRequestParameterException("Invalid 'role' value: 'invalid'"));

        mockMvc.perform(get("/api/v1/admin/users").param("role", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsUser() throws Exception {
        when(adminUserService.getUser(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ali@example.com"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        when(adminUserService.getUser(99L)).thenThrow(new UserNotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/v1/admin/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_returnsUpdatedUser() throws Exception {
        when(adminUserService.updateStatus(1L, "SUSPENDED")).thenReturn(response(UserStatus.SUSPENDED));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updateStatus_returns409_whenTransitionInvalid() throws Exception {
        when(adminUserService.updateStatus(1L, "SUSPENDED"))
                .thenThrow(new InvalidUserStatusTransitionException("User 1 cannot move from SUSPENDED to SUSPENDED"));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_returns400_whenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_returns404_whenMissing() throws Exception {
        when(adminUserService.updateStatus(99L, "SUSPENDED"))
                .thenThrow(new UserNotFoundException("User not found: 99"));

        mockMvc.perform(patch("/api/v1/admin/users/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isNotFound());
    }

    private AdminUserResponse response() {
        return response(UserStatus.ACTIVE);
    }

    private AdminUserResponse response(UserStatus status) {
        return AdminUserResponse.builder()
                .id(1L)
                .name("Ali")
                .email("ali@example.com")
                .role(Role.CUSTOMER)
                .status(status)
                .build();
    }
}
