package com.food.foodapp.auth.dto;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserResponse {

    private Long id;
    private String name;
    private String email;
    private Role role;
    private UserStatus status;
    private LocalDateTime joinedAt;
}
