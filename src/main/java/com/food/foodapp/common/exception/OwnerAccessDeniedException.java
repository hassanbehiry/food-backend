package com.food.foodapp.common.exception;

/**
 * Thrown when an authenticated caller acts on an {@code /api/v1/owner/**} resource for a restaurant
 * they do not own. Maps to {@code 403} via {@link GlobalExceptionHandler} — the restaurant exists,
 * the caller is authenticated, they simply are not its owner. Anonymous callers never reach this:
 * {@code SecurityConfig} rejects them with {@code 401} at the filter chain first.
 */
public class OwnerAccessDeniedException extends RuntimeException {

    public OwnerAccessDeniedException(String message) {
        super(message);
    }
}
