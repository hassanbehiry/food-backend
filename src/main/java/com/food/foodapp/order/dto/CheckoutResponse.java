package com.food.foodapp.order.dto;

import com.food.foodapp.cart.dto.CartItemResponse;
import com.food.foodapp.order.entity.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * The computed checkout-review summary. Nothing here is persisted — {@code POST /orders} repeats
 * every validation and recalculation from scratch rather than trusting any field echoed back
 * from this response, since cart/price/availability/address state can change between the two
 * calls.
 */
@Getter
@Builder
public class CheckoutResponse {

    private Long restaurantId;
    private String restaurantName;
    private List<CartItemResponse> items;
    private Long addressId;
    private String deliveryAddress;
    private PaymentMethod paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private BigDecimal total;
}
