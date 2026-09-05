package com.food.foodapp.auth.security;

import com.food.foodapp.auth.jwt.JwtUtil;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Wires the application onto Spring Security.
 *
 * <p>{@code /api/v1/admin/**} requires the {@code ADMIN} authority and {@code /api/v1/owner/**}
 * requires authentication; every other request is permitted at the filter-chain level, with
 * per-customer authentication still enforced deeper in the stack by feature services that call
 * {@link UserContext}. Owner routes additionally get a per-restaurant ownership check in the
 * service layer ({@code RestaurantOwnershipGuard}); the filter-chain rule here only guarantees
 * the caller is authenticated so that check has an identity to compare against.
 * {@link JwtCookieAuthenticationFilter} runs on every
 * request and, when a valid {@code auth_token} cookie is present, publishes the caller — id plus a
 * {@code ROLE_<name>} authority — into the
 * {@link org.springframework.security.core.context.SecurityContextHolder}.
 *
 * <p>Admin authorization <em>is</em> defined here, at the filter chain, rather than with per-controller
 * checks: {@code /api/v1/admin/**} requires the {@code ADMIN} authority, so every current and future
 * admin controller is covered by one rule and admin services stay free of authorization code.
 * Unauthenticated callers get 401 via {@link RestAuthenticationEntryPoint}; authenticated non-admins
 * get 403 via {@link RestAccessDeniedHandler}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtCookieAuthenticationFilter(jwtUtil);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter,
                                            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                                            RestAccessDeniedHandler restAccessDeniedHandler) throws Exception {
        return http
                // Stateless JWT-cookie auth: no CSRF tokens, no server-side session.
                .csrf(csrf -> csrf.disable())
                // Keep the CORS rules already declared in common.config.WebConfig in effect.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/owner/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtCookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * The JWT filter must run only inside the Spring Security chain (added above). This disables the
     * standalone servlet-filter registration Spring Boot would otherwise create for any {@code Filter}
     * bean, so it isn't invoked twice per request.
     */
    @Bean
    FilterRegistrationBean<JwtCookieAuthenticationFilter> jwtCookieAuthenticationFilterRegistration(
            JwtCookieAuthenticationFilter filter) {
        FilterRegistrationBean<JwtCookieAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
