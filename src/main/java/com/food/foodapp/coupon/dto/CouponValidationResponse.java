package com.food.foodapp.coupon.dto;

import com.food.foodapp.coupon.entity.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A preview of what applying this coupon would do to the caller's current cart — nothing is
 * persisted by this endpoint. {@code POST /cart/checkout} and {@code POST /orders} recompute the
 * same thing independently from the {@code couponCode} on {@code CheckoutRequest}, so nothing
 * from this response is ever echoed back and trusted.
 */
@Getter
@Builder
public class CouponValidationResponse {

    private String code;
    private DiscountType discountType;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private BigDecimal total;
}
