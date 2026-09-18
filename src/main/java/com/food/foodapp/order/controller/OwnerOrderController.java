package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.DeliveryDashboardResponse;
import com.food.foodapp.order.dto.OrderDeliveryResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OwnerOrderListResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerOrderStatusUpdateRequest;
import com.food.foodapp.order.dto.SendToDeliveryRequest;
import com.food.foodapp.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
     * Supports {@code status} (confirmed | delivered | cancelled — "all" is the default when
     * {@code status} is omitted) and pagination.
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

    /**
     * PATCH /api/v1/owner/restaurants/{restaurantId}/orders/{orderId}/status
     * Accepts {@code preparing}, {@code ready_for_delivery}, or {@code cancelled} — see
     * {@code OrderService}'s {@code OWNER_REQUESTABLE_STATUSES}. Dispatch ({@code
     * OUT_FOR_DELIVERY}) and delivery confirmation ({@code DELIVERED}) each have their own
     * dedicated endpoint below instead, since both need to write more than just the status column.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long restaurantId, @PathVariable Long orderId,
            @Valid @RequestBody OwnerOrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, orderId, request.getStatus()));
    }

    /**
     * POST /api/v1/owner/restaurants/{restaurantId}/orders/{orderId}/send-to-delivery
     * Dispatches a {@code READY_FOR_DELIVERY} order to a courier: flips it to {@code
     * OUT_FOR_DELIVERY}, stamps {@code sentToDeliveryAt}, and records the optional
     * {@code deliveryPersonName} from the request body (a plain label — see {@code Order}, there
     * is no dedicated courier account in this system). {@code 409} if the order isn't currently
     * {@code READY_FOR_DELIVERY}.
     */
    @PostMapping("/{orderId}/send-to-delivery")
    public ResponseEntity<OrderResponse> sendToDelivery(
            @PathVariable Long restaurantId, @PathVariable Long orderId,
            @RequestBody(required = false) SendToDeliveryRequest request) {
        String deliveryPersonName = request == null ? null : request.getDeliveryPersonName();
        return ResponseEntity.ok(orderService.sendToDelivery(restaurantId, orderId, deliveryPersonName));
    }

    /**
     * POST /api/v1/owner/restaurants/{restaurantId}/orders/{orderId}/deliver
     * The restaurant/delivery side confirming an {@code OUT_FOR_DELIVERY} order has reached the
     * customer: flips it to {@code DELIVERED} and atomically records the order's revenue (see
     * {@code OrderService#deliverOrder}). This is independent of, and can race safely against, the
     * customer's own {@code PUT /orders/{id}/confirm-delivery} — whichever lands first wins;
     * {@code 409} on the order that arrives second, or on any order that isn't currently
     * {@code OUT_FOR_DELIVERY}.
     */
    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<OrderDeliveryResponse> deliver(
            @PathVariable Long restaurantId, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.deliverOrder(restaurantId, orderId));
    }

    /**
     * GET /api/v1/owner/restaurants/{restaurantId}/orders/delivery-dashboard
     * The owner dashboard's Delivery Orders section: summary KPI cards plus every order currently
     * {@code OUT_FOR_DELIVERY} — see {@code OrderService#getDeliveryDashboard}.
     */
    @GetMapping("/delivery-dashboard")
    public ResponseEntity<DeliveryDashboardResponse> deliveryDashboard(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(orderService.getDeliveryDashboard(restaurantId));
    }
}
