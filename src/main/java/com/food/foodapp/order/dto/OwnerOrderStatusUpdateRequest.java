package com.food.foodapp.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code status} is a raw string rather than the {@link com.food.foodapp.order.entity.OrderStatus}
 * enum directly so an unsupported value fails as a clean, validated 400 (see
 * {@code OrderService#updateOrderStatus}) instead of a raw JSON-deserialization error — same
 * convention {@code CheckoutRequest#paymentMethod} uses.
 */
@Getter
@Setter
public class OwnerOrderStatusUpdateRequest {

    @NotBlank(message = "status is required")
    private String status;
}
