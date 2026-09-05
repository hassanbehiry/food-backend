package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AuthResponse;
import com.food.foodapp.auth.dto.LoginRequest;
import com.food.foodapp.auth.dto.RegisterRequest;
import com.food.foodapp.auth.dto.UserResponse;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.AccountSuspendedException;
import com.food.foodapp.common.exception.DuplicateEmailException;
import com.food.foodapp.common.exception.InvalidCredentialsException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
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
     * <p>
     * Self-service registration can only ever create a {@code CUSTOMER} or an {@code OWNER}.
     * {@code ADMIN} (and any role added later) is rejected here so the public endpoint can never be
     * used to mint a privileged account — admins are provisioned out of band, see
     * {@link com.food.foodapp.auth.bootstrap.AdminAccountInitializer}.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() != Role.CUSTOMER && request.getRole() != Role.OWNER) {
            throw new InvalidRequestParameterException("role must be CUSTOMER or OWNER");
        }

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
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }

        // Save to database
        userRepository.save(user);

        // Return success message only — no JWT cookie on registration
        return AuthResponse.builder()
                .message("Registration successful")
                .build();
    }

    /**
     * Authenticates a user and generates a JWT token.
     * Flow: normalize email → find user → verify password → reject if suspended → generate JWT.
     * Returns the JWT token string + AuthResponse with user info.
     * <p>
     * The suspension check runs after password verification, not before, so a suspended account
     * isn't distinguishable from a wrong password to a caller who doesn't already know the
     * correct one.
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

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException("This account has been suspended");
        }

        // Generate JWT
        String token = jwtUtil.generateToken(user);

        // Build user response (never include password)
        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();

        // Also surface the token in the response body (in addition to the HttpOnly cookie the
        // controller sets from LoginResult.token()) so callers that cannot rely on cookies — a
        // cross-site SPA, a native client — can authenticate via `Authorization: Bearer <token>`
        // instead. See JwtCookieAuthenticationFilter, which accepts either transport.
        AuthResponse authResponse = AuthResponse.builder()
                .message("Login successful")
                .user(userResponse)
                .token(token)
                .build();

        return new LoginResult(token, authResponse);
    }

    /**
     * Simple record to hold both the JWT token and the response.
     * The controller uses the token to set the HttpOnly cookie.
     */
    public record LoginResult(String token, AuthResponse response) {}
}
