package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.CheckoutRequest;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer order endpoints: step two of checkout (place the order), lookup, and
 * customer-initiated cancellation. Thin controller — {@link OrderService} resolves the caller
 * itself via {@code UserContext} and enforces that an order can only be read or cancelled by the
 * customer who placed it.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** POST /api/v1/orders */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    /** GET /api/v1/orders/{orderId} */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    /** GET /api/v1/orders/{orderId}/track */
    @GetMapping("/{orderId}/track")
    public ResponseEntity<OrderTrackingResponse> trackOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.trackOrder(orderId));
    }

    /** POST /api/v1/orders/{orderId}/cancel */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }
}
