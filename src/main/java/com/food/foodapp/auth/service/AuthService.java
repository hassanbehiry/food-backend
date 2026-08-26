package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AuthResponse;
import com.food.foodapp.auth.dto.LoginRequest;
import com.food.foodapp.auth.dto.RegisterRequest;
import com.food.foodapp.auth.dto.UserResponse;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.DuplicateEmailException;
import com.food.foodapp.common.exception.InvalidCredentialsException;
import com.food.foodapp.common.exception.RestaurantRegistrationClosedException;
import com.food.foodapp.settings.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contains all authentication business logic.
 * Controller delegates to this service — no business logic in controllers.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PlatformSettingsService platformSettingsService;

    /**
     * Registers a new user.
     * Flow: validate → normalize email → check duplicate → hash password → save.
     * Returns AuthResponse with message only (no JWT, no user data).
     * <p>
     * {@code OWNER} sign-up is additionally gated by the admin-controlled
     * {@link PlatformSettingsService#isRestaurantRegistrationAllowed()} toggle — {@code CUSTOMER}
     * sign-up is never affected by it.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() == Role.OWNER && !platformSettingsService.isRestaurantRegistrationAllowed()) {
            throw new RestaurantRegistrationClosedException("Restaurant registration is currently closed");
        }

        // Normalize email: lowercase + trim
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Check if email already exists
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email already exists");
        }

        // Create and populate user entity
        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        // Save to database
        userRepository.save(user);

        // Return success message only — no JWT cookie on registration
        return AuthResponse.builder()
                .message("Registration successful")
                .build();
    }

    /**
     * Authenticates a user and generates a JWT token.
     * Flow: normalize email → find user → verify password → generate JWT.
     * Returns the JWT token string + AuthResponse with user info.
     */
    public LoginResult login(LoginRequest request) {
        // Normalize email
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Find user by email
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Generate JWT
        String token = jwtUtil.generateToken(user);

        // Build user response (never include password)
        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .message("Login successful")
                .user(userResponse)
                .build();

        return new LoginResult(token, authResponse);
    }

    /**
     * Simple record to hold both the JWT token and the response.
     * The controller uses the token to set the HttpOnly cookie.
     */
    public record LoginResult(String token, AuthResponse response) {}
}
