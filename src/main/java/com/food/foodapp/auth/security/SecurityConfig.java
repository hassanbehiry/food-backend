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
 * Wires the application onto Spring Security without changing its externally observable behavior.
 *
 * <p>Every request is permitted at the filter-chain level. Authorization is still enforced deeper
 * in the stack, by feature services that call {@link UserContext}. What this configuration adds is
 * that {@link JwtCookieAuthenticationFilter} now runs on every request and, when a valid
 * {@code auth_token} cookie is present, publishes the caller into the
 * {@link org.springframework.security.core.context.SecurityContextHolder} — making the
 * SecurityContext the single source of truth for the current user id, which
 * {@link SecurityContextHolderUserContext} reads.
 *
 * <p>Endpoint-level authorization rules (public vs. authenticated vs. role-gated) are intentionally
 * not defined here yet; introducing them is a separate, deliberate change that will only touch this
 * class.
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
                                            JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter) throws Exception {
        return http
                // Stateless JWT-cookie auth: no CSRF tokens, no server-side session.
                .csrf(csrf -> csrf.disable())
                // Keep the CORS rules already declared in common.config.WebConfig in effect.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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
