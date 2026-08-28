package com.food.foodapp.auth.security;

import com.food.foodapp.common.exception.UnauthenticatedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@link UserContext} backed by Spring Security's {@link SecurityContextHolder}.
 * {@link JwtCookieAuthenticationFilter} populates the context from the {@code auth_token} cookie
 * earlier in the request; this reads the user id back out of it.
 *
 * <p>This is the permanent replacement for the interim {@code JwtCookieUserContext}. No caller of
 * {@link UserContext} changed when it was swapped in — that was the point of the interface.
 */
@Component
public class SecurityContextHolderUserContext implements UserContext {

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Authentication required");
        }
        if (authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new UnauthenticatedException("Authentication required");
    }
}
