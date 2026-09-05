package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.ProfileResponse;
import com.food.foodapp.auth.dto.UpdateProfileRequest;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current user's own profile. The caller is always resolved via {@link UserContext};
 * no method here accepts a user id from the caller. Backs {@code GET /api/v1/auth/me} and
 * {@code GET|PUT /api/v1/user/profile}.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserContext userContext;

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile() {
        return toResponse(loadCurrentUser());
    }

    @Transactional
    public ProfileResponse updateCurrentProfile(UpdateProfileRequest request) {
        User user = loadCurrentUser();

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().isBlank() ? null : request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().isBlank() ? null : request.getAvatarUrl().trim());
        }

        return toResponse(userRepository.save(user));
    }

    private User loadCurrentUser() {
        Long userId = userContext.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private ProfileResponse toResponse(User user) {
        return ProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
