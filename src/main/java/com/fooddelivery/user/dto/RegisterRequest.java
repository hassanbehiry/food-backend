package com.fooddelivery.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).*$",
            message = "Password must contain at least one letter and one number"
    )
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^01[0-2,5]{1}[0-9]{8}$", message = "Phone number must be a valid Egyptian mobile number")
    private String phone;

    /**
     * Optional. Accepts "CUSTOMER" or "RESTAURANT_OWNER" only (case-insensitive).
     * "ADMIN" cannot be self-assigned through public registration for security reasons.
     * Defaults to CUSTOMER when omitted or blank.
     */
    @Pattern(
            regexp = "^(?i)(CUSTOMER|RESTAURANT_OWNER)?$",
            message = "Role must be either CUSTOMER or RESTAURANT_OWNER"
    )
    private String role;
}
