package com.food.foodapp.common.exception;

/**
 * Thrown when a presented password-reset token is unknown, already used, or expired. Maps to
 * {@code 400} via {@link GlobalExceptionHandler} with a deliberately generic message
 * ("Invalid or expired reset token") so the three cases are indistinguishable to a caller.
 */
public class PasswordResetTokenInvalidException extends RuntimeException {

    public PasswordResetTokenInvalidException(String message) {
        super(message);
    }
}
