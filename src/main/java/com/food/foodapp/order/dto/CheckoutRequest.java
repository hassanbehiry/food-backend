package com.food.foodapp.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * The customer's checkout choices — shared by the preview ({@code POST /cart/checkout}) and
 * order-creation ({@code POST /orders}) endpoints, since both must validate and recompute from
 * exactly the same inputs. {@code paymentMethod} is a raw string rather than the
 * {@code PaymentMethod} enum directly so an unsupported value fails as a clean, validated 400
 * (see {@code OrderService}) instead of a raw JSON-deserialization error.
 * <p>
 * The delivery destination is given <b>either</b> as {@code addressId} (a saved {@code Address}
 * owned by the caller) <b>or</b> as an inline one-off address ({@code street} + {@code city}
 * required, {@code postalCode}/{@code notes}/{@code label} optional). Exactly one of the two must
 * be supplied — see {@link #isExactlyOneDeliveryTargetPresent()}. The inline address is snapshotted
 * straight onto the order's flat {@code delivery_*} columns; it never creates a saved
 * {@code Address} row.
 * <p>
 * {@code couponCode} is optional and, like every other field here, re-validated from scratch by
 * both endpoints via {@code CouponService} — the discount it yields is never accepted from the
 * caller, only the code. There is deliberately no {@code items}/{@code subtotal}/{@code total}
 * field: the cart is server-authoritative and every amount is recomputed in {@code OrderService}.
 */
@Getter
@Setter
public class CheckoutRequest {

    /** A saved address owned by the caller. Mutually exclusive with the inline fields below. */
    private Long addressId;

    /** Inline one-off address — required together when {@code addressId} is absent. */
    private String street;
    private String city;

    /** Inline one-off address — optional even on the inline path. */
    private String postalCode;
    private String notes;
    private String label;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;

    private String couponCode;

    /**
     * Exactly one delivery target: a saved {@code addressId}, or an inline address carrying at
     * least {@code street} and {@code city}. Both or neither -> 400.
     */
    @JsonIgnore
    @AssertTrue(message = "Provide exactly one of: addressId, or an inline address with street and city")
    public boolean isExactlyOneDeliveryTargetPresent() {
        boolean hasAddressId = addressId != null;
        boolean hasInlineAddress = isNotBlank(street) && isNotBlank(city);
        return hasAddressId ^ hasInlineAddress;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
