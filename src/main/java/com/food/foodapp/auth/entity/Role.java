package com.food.foodapp.auth.entity;

/**
 * A user's single account role. Persisted as a string on {@code users.role} ({@code EnumType.STRING})
 * and copied into the {@code role} claim of the auth JWT by {@link com.food.foodapp.auth.jwt.JwtUtil},
 * from where {@link com.food.foodapp.auth.security.JwtCookieAuthenticationFilter} turns it into the
 * Spring Security authority {@code ROLE_<name>}.
 * <p>
 * {@code ADMIN} is the platform operator and is deliberately not obtainable through self-service
 * registration — see {@link com.food.foodapp.auth.service.AuthService#register} for the guard and
 * {@link com.food.foodapp.auth.bootstrap.AdminAccountInitializer} for how the first admin is seeded.
 */
public enum Role {
    CUSTOMER,
    OWNER,
    ADMIN
}
