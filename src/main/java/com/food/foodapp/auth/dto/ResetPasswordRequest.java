package com.food.foodapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of {@code POST /api/v1/auth/reset-password}: the raw token from the emailed link plus the
 * replacement password. {@code newPassword} carries the same {@code min = 8} rule as registration
 * so a reset can never weaken an account below the sign-up bar.
 */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
