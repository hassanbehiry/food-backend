package com.food.foodapp.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of {@code PUT /api/v1/user/profile}. Every field is optional: a field that is absent
 * (null) leaves the stored value unchanged, so a form that only edits one thing need not
 * resend the rest. There is deliberately no {@code email} field — changing the login
 * identity needs its own re-authentication flow and is out of scope here.
 */
@Getter
@Setter
public class UpdateProfileRequest {

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    @Size(max = 512, message = "Avatar URL must be at most 512 characters")
    private String avatarUrl;
}
