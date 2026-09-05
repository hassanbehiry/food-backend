package com.food.foodapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * The current user's own profile, returned by {@code GET /api/v1/auth/me} and
 * {@code GET|PUT /api/v1/user/profile}. {@code email} is included for display but is not
 * editable through the profile endpoints — it is the login key and a JWT claim.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private LocalDateTime createdAt;
}
