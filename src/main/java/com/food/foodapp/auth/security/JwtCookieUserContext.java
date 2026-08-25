package com.food.foodapp.auth.security;

import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.common.exception.UnauthenticatedException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Temporary {@link UserContext} implementation: resolves the caller from the
 * {@code auth_token} JWT cookie set by {@code AuthController} on login. Standing in
 * until this codebase adopts Spring Security, at which point this should be replaced
 * by a {@code SecurityContextHolder}-backed implementation — no caller of
 * {@link UserContext} needs to change when that happens.
 */
@Component
@RequiredArgsConstructor
public class JwtCookieUserContext implements UserContext {

    private static final String AUTH_COOKIE_NAME = "auth_token";

    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    @Override
    public Long getCurrentUserId() {
        String token = extractToken();
        if (token == null) {
            throw new UnauthenticatedException("Authentication required");
        }
        try {
            return jwtUtil.parseUserId(token);
        } catch (JwtException | NumberFormatException e) {
            throw new UnauthenticatedException("Invalid or expired session");
        }
    }

    private String extractToken() {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> AUTH_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
