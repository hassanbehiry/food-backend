package com.food.foodapp.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * The customer's checkout choices — shared by the preview ({@code POST /cart/checkout}) and
 * order-creation ({@code POST /orders}) endpoints, since both must validate and recompute from
 * exactly the same inputs. {@code paymentMethod} is a raw string rather than the
 * {@code PaymentMethod} enum directly so an unsupported value fails as a clean, validated 400
 * (see {@code OrderService}) instead of a raw JSON-deserialization error.
 */
@Getter
@Setter
public class CheckoutRequest {

    @NotNull(message = "addressId is required")
    private Long addressId;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;
}
