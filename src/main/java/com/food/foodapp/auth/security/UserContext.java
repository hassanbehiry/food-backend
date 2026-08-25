package com.food.foodapp.auth.security;

/**
 * Resolves the identity of the caller making the current request. Feature services
 * depend on this interface rather than any particular authentication mechanism, so
 * the underlying resolution strategy can be swapped (e.g. for a
 * {@code SecurityContextHolder}-backed implementation once this codebase adopts
 * Spring Security) without touching any caller.
 */
public interface UserContext {

    /**
     * @return the authenticated user's id
     * @throws com.food.foodapp.common.exception.UnauthenticatedException if no valid user can be resolved
     */
    Long getCurrentUserId();
}
