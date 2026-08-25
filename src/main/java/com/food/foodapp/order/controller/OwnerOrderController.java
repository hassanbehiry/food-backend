package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OwnerOrderStatusUpdateRequest;
import com.food.foodapp.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant-owner order-status action: requests a valid forward transition for one order,
 * through the same centralized transition logic {@link OrderController#cancelOrder} uses. Thin
 * controller — {@link OrderService} enforces legality and restaurant scoping.
 * <p>
 * Listing/filtering/paginating a restaurant's orders and the owner dashboard overview are a
 * separate, larger feature (restaurant-owner order management) layered on top of this once it
 * exists; this endpoint is only the status-transition action this workflow requires.
 */
@RestController
@RequestMapping("/api/v1/owner/restaurants/{restaurantId}/orders")
@RequiredArgsConstructor
public class OwnerOrderController {

    private final OrderService orderService;

    /** PATCH /api/v1/owner/restaurants/{restaurantId}/orders/{orderId}/status */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long restaurantId, @PathVariable Long orderId,
            @Valid @RequestBody OwnerOrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, orderId, request.getStatus()));
    }
}
