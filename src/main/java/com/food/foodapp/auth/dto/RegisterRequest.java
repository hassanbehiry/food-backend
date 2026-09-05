package com.food.foodapp.auth.dto;

import com.food.foodapp.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Optional. The frontend RegisterPage collects a phone number; it is persisted when present.
     * Not {@code @NotBlank} — existing API clients and tests that register without a phone must
     * keep working, and there is no meaningful value to backfill for accounts created before this.
     */
    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    /**
     * The restaurant name for an owner sign-up. Required when {@code role == OWNER} (validated in
     * {@link com.food.foodapp.auth.service.AuthService#register}); ignored for a customer. The
     * owner's restaurant is created in {@code PENDING} approval status and the owner configures the
     * rest (cuisine, delivery fee, hours) afterward through the owner settings endpoint.
     */
    @Size(max = 150, message = "Restaurant name must be at most 150 characters")
    private String restaurantName;
}
