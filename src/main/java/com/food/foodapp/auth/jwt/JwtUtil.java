package com.food.foodapp.auth.jwt;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility for generating and parsing JWT tokens.
 * Reads configuration from application.properties.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * Generates a JWT containing userId, email, and role as claims.
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a JWT, returning the user id stored as its subject.
     *
     * @throws JwtException if the token is missing, malformed, expired, or has an invalid signature
     */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /**
     * Parses and validates a JWT, returning both the user id (subject) and the {@link Role} carried
     * in the {@code role} claim. Used by {@link com.food.foodapp.auth.security.JwtCookieAuthenticationFilter}
     * to build the Spring Security authority for the request, so authorization no longer needs a
     * database round-trip per call.
     *
     * @throws JwtException             if the token is missing, malformed, expired, or wrongly signed
     * @throws IllegalArgumentException if the subject is not numeric or the {@code role} claim is
     *                                  missing/unknown
     */
    public AuthenticatedUser authenticate(String token) {
        Claims claims = parseClaims(token);
        Long userId = Long.valueOf(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        return new AuthenticatedUser(userId, role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** The caller identity a valid auth token resolves to. */
    public record AuthenticatedUser(Long userId, Role role) {}
}
