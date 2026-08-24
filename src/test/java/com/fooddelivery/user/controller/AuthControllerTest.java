package com.fooddelivery.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_withValidData_returnsCreatedAndToken() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ahmed Ali", "ahmed.ali@example.com", "Passw0rd123", "01012345678", "CUSTOMER"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.email").value("ahmed.ali@example.com"));
    }

    @Test
    void register_withDuplicateEmail_returnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ahmed Ali", "duplicate@example.com", "Passw0rd123", "01012345678", null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_withInvalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Ahmed Ali", "not-an-email", "Passw0rd123", "01012345678", null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withAdminRole_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Malicious User", "hacker@example.com", "Passw0rd123", "01011122233", "ADMIN"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "Sara Youssef", "sara.youssef@example.com", "Passw0rd123", "01098765432", "RESTAURANT_OWNER"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        LoginRequest loginRequest = new LoginRequest("sara.youssef@example.com", "WrongPassword1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}
