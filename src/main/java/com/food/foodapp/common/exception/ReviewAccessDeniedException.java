package com.food.foodapp.common.exception;

/**
 * Thrown when a customer tries to review an order that is not theirs. Distinct from
 * {@link ReviewNotEligibleException} because the two map to different statuses: this is a
 * {@code 403} (the order exists, the caller just may not act on it), whereas an order that is
 * theirs but not yet delivered is a {@code 409}. Mapped via {@link GlobalExceptionHandler}.
 * <p>
 * Note the codebase's more common pattern for "not yours" is a 404 that hides existence
 * (customer-scoped repository lookups); the review write path deliberately returns 403 here per
 * the BACKEND-005 spec so the client can tell "not found" from "not permitted".
 */
public class ReviewAccessDeniedException extends RuntimeException {

    public ReviewAccessDeniedException(String message) {
        super(message);
    }
}
