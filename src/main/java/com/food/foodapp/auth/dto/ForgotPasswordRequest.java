package com.food.foodapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of {@code POST /api/v1/auth/forgot-password}. The endpoint always responds {@code 200} with
 * a generic message regardless of whether an account exists for {@code email}, so this request
 * never reveals account existence.
 */
@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
