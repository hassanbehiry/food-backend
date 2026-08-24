package com.fooddelivery.user.service;

import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import com.fooddelivery.user.entity.Role;
import com.fooddelivery.user.entity.User;
import com.fooddelivery.user.exception.EmailAlreadyExistsException;
import com.fooddelivery.user.exception.InvalidCredentialsException;
import com.fooddelivery.user.repository.UserRepository;
import com.fooddelivery.user.security.JwtService;
import com.fooddelivery.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone().trim())
                .role(resolveRegistrationRole(request.getRole()))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        return buildAuthResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        return buildAuthResponse(user);
    }

    /**
     * Public registration may only create CUSTOMER or RESTAURANT_OWNER accounts.
     * ADMIN accounts must be provisioned separately (e.g. by an existing admin), never self-assigned.
     */
    private Role resolveRegistrationRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return Role.CUSTOMER;
        }

        Role role = Role.valueOf(requestedRole.trim().toUpperCase());

        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Role must be either CUSTOMER or RESTAURANT_OWNER");
        }

        return role;
    }

    private AuthResponse buildAuthResponse(User user) {
        UserPrincipal principal = new UserPrincipal(user);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId());
        extraClaims.put("role", user.getRole().name());

        String token = jwtService.generateToken(principal, extraClaims);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
