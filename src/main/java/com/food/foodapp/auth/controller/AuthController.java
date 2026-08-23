package com.food.foodapp.auth.controller;

import com.food.foodapp.auth.dto.AuthResponse;
import com.food.foodapp.auth.dto.LoginRequest;
import com.food.foodapp.auth.dto.RegisterRequest;
import com.food.foodapp.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Handles authentication HTTP requests.
 * Thin controller — all business logic is in AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Value("${cookie.same-site}")
    private String cookieSameSite;

    /**
     * POST /api/v1/auth/register
     * Registers a new user. Does NOT create a JWT cookie.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Authenticates user and sets JWT as HttpOnly cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse servletResponse) {
        AuthService.LoginResult result = authService.login(request);

        // Set JWT as HttpOnly cookie — JavaScript cannot access it
        ResponseCookie cookie = ResponseCookie.from("auth_token", result.token())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofMillis(jwtExpiration))
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(result.response());
    }

    /**
     * POST /api/v1/auth/logout
     * Clears the auth_token cookie by setting Max-Age=0.
     */
    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletResponse servletResponse) {
        // Clear cookie by setting Max-Age=0
        ResponseCookie cookie = ResponseCookie.from("auth_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        AuthResponse response = AuthResponse.builder()
                .message("Logout successful")
                .build();
        return ResponseEntity.ok(response);
    }
}
