package com.food.foodapp.auth.security;

/**
 * Resolves the identity of the caller making the current request. Feature services
 * depend on this interface rather than any particular authentication mechanism, so
 * the underlying resolution strategy can be swapped without touching any caller — as
 * it already was once, from a cookie-parsing implementation to the current
 * {@link SecurityContextHolderUserContext}, when this codebase adopted Spring Security.
 */
public interface UserContext {

    /**
     * @return the authenticated user's id
     * @throws com.food.foodapp.common.exception.UnauthenticatedException if no valid user can be resolved
     */
    Long getCurrentUserId();
}
