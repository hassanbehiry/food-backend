package com.food.foodapp.auth.mapper;

import com.food.foodapp.auth.dto.AdminUserResponse;
import com.food.foodapp.auth.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static AdminUserResponse toAdminResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .joinedAt(user.getCreatedAt())
                .build();
    }
}
