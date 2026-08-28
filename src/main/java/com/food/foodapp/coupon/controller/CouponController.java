package com.food.foodapp.coupon.controller;

import com.food.foodapp.coupon.dto.CouponValidateRequest;
import com.food.foodapp.coupon.dto.CouponValidationResponse;
import com.food.foodapp.coupon.mapper.CouponMapper;
import com.food.foodapp.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standalone coupon preview, for a cart-page "apply coupon" affordance ahead of checkout. Nothing
 * here is persisted — {@code POST /cart/checkout} and {@code POST /orders} independently
 * revalidate any {@code couponCode} submitted on {@link com.food.foodapp.order.dto.CheckoutRequest},
 * since this preview's result is never trusted back. Thin controller — {@link CouponService}
 * resolves the caller's cart itself via {@code UserContext}.
 */
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** POST /api/v1/coupons/validate */
    @PostMapping("/validate")
    public ResponseEntity<CouponValidationResponse> validate(@Valid @RequestBody CouponValidateRequest request) {
        CouponService.CouponApplicationPreview preview = couponService.validateForCurrentCart(request.getCode());
        return ResponseEntity.ok(CouponMapper.toValidationResponse(preview));
    }
}
