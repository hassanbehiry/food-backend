package com.food.foodapp.auth.security;

import com.food.foodapp.auth.jwt.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the {@code auth_token} JWT cookie set by {@code AuthController} on login and, when it is
 * present and valid, publishes the resolved user id into the {@link SecurityContextHolder} as the
 * authenticated principal. A missing or invalid cookie leaves the context unauthenticated — this
 * filter never rejects a request. Endpoints that require a caller still fail later, via
 * {@link SecurityContextHolderUserContext#getCurrentUserId()}.
 *
 * <p>This replaces the request-scoped {@code JwtCookieUserContext}: cookie parsing now happens
 * once per request here instead of lazily inside the {@link UserContext} implementation, which
 * makes the SecurityContext the single source of truth for "who is calling".
 *
 * <p>The token's {@code role} claim is turned into a single {@code ROLE_<name>} authority on the
 * published {@code Authentication}, which is what lets {@link SecurityConfig} gate
 * {@code /api/v1/admin/**} with {@code hasRole("ADMIN")} without any per-request database lookup.
 *
 * <p>Registered only inside the Spring Security filter chain by {@link SecurityConfig} — it is not
 * a component-scanned bean, so it stays out of {@code @WebMvcTest} slices.
 */
@RequiredArgsConstructor
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    static final String AUTH_COOKIE_NAME = "auth_token";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromCookie(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateFromCookie(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return;
        }
        try {
            JwtUtil.AuthenticatedUser user = jwtUtil.authenticate(token);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user.userId(), null, authorities));
        } catch (JwtException | IllegalArgumentException e) {
            // Malformed, expired, wrongly-signed, or claim-incomplete token — leave the context
            // unauthenticated. (NumberFormatException, from a non-numeric subject, is an
            // IllegalArgumentException.)
        }
    }

    private String extractToken(HttpServletRequest request) {
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
