package com.food.foodapp.coupon.mapper;

import com.food.foodapp.coupon.dto.CouponValidationResponse;
import com.food.foodapp.coupon.service.CouponService.CouponApplicationPreview;

import java.math.BigDecimal;

public final class CouponMapper {

    private CouponMapper() {
    }

    public static CouponValidationResponse toValidationResponse(CouponApplicationPreview preview) {
        BigDecimal subtotal = preview.subtotal();
        BigDecimal deliveryFee = preview.deliveryFee();
        BigDecimal discount = preview.application().discount();
        return CouponValidationResponse.builder()
                .code(preview.application().coupon().getCode())
                .discountType(preview.application().coupon().getDiscountType())
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .discount(discount)
                .total(subtotal.add(deliveryFee).subtract(discount))
                .build();
    }
}
