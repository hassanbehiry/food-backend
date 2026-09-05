package com.food.foodapp.auth.security;

import com.food.foodapp.auth.jwt.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Authenticates each request from either of two transports carrying the same JWT issued on login:
 * an {@code Authorization: Bearer <token>} header, or the {@code auth_token} HttpOnly cookie set by
 * {@code AuthController}. The header is checked first; the cookie is the fallback when no bearer
 * header is present. Whichever transport supplies the token, it is validated by the exact same
 * {@link JwtUtil#authenticate(String)} call, so both paths resolve to the same Spring Security
 * principal/authorities — there is no separate or weaker validation for either one. When a valid
 * token is found, the resolved user id is published into the {@link SecurityContextHolder} as the
 * authenticated principal. A missing or invalid token on both transports leaves the context
 * unauthenticated — this filter never rejects a request itself. Endpoints that require a caller
 * still fail later, via {@link SecurityContextHolderUserContext#getCurrentUserId()}.
 *
 * <p>This replaces the request-scoped {@code JwtCookieUserContext}: token parsing now happens once
 * per request here instead of lazily inside the {@link UserContext} implementation, which makes the
 * SecurityContext the single source of truth for "who is calling".
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
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromToken(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateFromToken(HttpServletRequest request) {
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

    /**
     * Prefers the {@code Authorization: Bearer <token>} header; falls back to the {@code auth_token}
     * cookie when no (or a non-Bearer) Authorization header is present. Both a header and a cookie
     * may legitimately be present on the same request (e.g. a browser client that also stores the
     * token for an API client to reuse) — the header wins in that case, but either alone is enough.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String bearerToken = header.substring(BEARER_PREFIX.length()).trim();
            if (!bearerToken.isEmpty()) {
                return bearerToken;
            }
        }
        return extractFromCookie(request);
    }

    private String extractFromCookie(HttpServletRequest request) {
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
