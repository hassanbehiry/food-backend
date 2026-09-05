package com.food.foodapp.common.exception;

/**
 * An admin attempted an action that is not permitted against the target account — currently
 * changing their own status, or changing another {@code ADMIN}'s status. Maps to HTTP 403:
 * the caller is authenticated and authorized for the admin surface, but this specific operation
 * on this specific target is disallowed.
 */
public class AdminActionForbiddenException extends RuntimeException {

    public AdminActionForbiddenException(String message) {
        super(message);
    }
}
