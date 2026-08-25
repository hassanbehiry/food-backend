package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerOrderStatusUpdateRequest;
import com.food.foodapp.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant-owner order management: paginated/filterable listing, detail, and the status-update
 * action. Thin controller — {@link OrderService} enforces legality and restaurant scoping.
 * <p>
 * The combined stats + recent-orders overview for the dashboard's landing view is a separate
 * endpoint, {@code OwnerDashboardController}, layered on top of {@link #list} rather than
 * replacing it.
 */
@RestController
@RequestMapping("/api/v1/owner/restaurants/{restaurantId}/orders")
@RequiredArgsConstructor
public class OwnerOrderController {

    private final OrderService orderService;

    /**
     * GET /api/v1/owner/restaurants/{restaurantId}/orders
     * Supports {@code status} (new | preparing | on_the_way | delivered — the dashboard's tabs
     * besides "all", which is the default when {@code status} is omitted) and pagination.
     */
    @GetMapping
    public ResponseEntity<OwnerOrderListResponse> list(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.listOrdersForOwner(restaurantId, status, page, size));
    }

    /** GET /api/v1/owner/restaurants/{restaurantId}/orders/{orderId} */
    @GetMapping("/{orderId}")
    public ResponseEntity<OwnerOrderResponse> getById(@PathVariable Long restaurantId, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderForOwner(restaurantId, orderId));
    }

    /** PATCH /api/v1/owner/restaurants/{restaurantId}/orders/{orderId}/status */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long restaurantId, @PathVariable Long orderId,
            @Valid @RequestBody OwnerOrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, orderId, request.getStatus()));
    }
}
