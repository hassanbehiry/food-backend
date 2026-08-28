package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.CheckoutRequest;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step one of the frontend's two-step checkout flow: validates the cart/address/payment choice
 * and returns a computed preview for the review screen. Nothing is persisted here — step two,
 * {@link OrderController#placeOrder}, repeats every validation and recalculation from scratch
 * rather than trusting this response. Thin controller — {@link OrderService} resolves the caller
 * itself via {@code UserContext}.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orderService;

    /** POST /api/v1/cart/checkout */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(orderService.previewCheckout(request));
    }
}
