package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The live tracking view of a placed order — deliberately separate from {@link OrderResponse}
 * (the immutable receipt): this always reflects current persisted status rather than a snapshot,
 * and adds the milestone progress and an ETA a tracking screen needs but a plain order-detail
 * screen doesn't. This codebase has no per-transition audit/event log yet, so {@code steps} is
 * derived from the current status against the canonical forward order rather than from stored
 * per-status timestamps.
 */
@Getter
@Builder
public class OrderTrackingResponse {

    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private List<TrackingStepResponse> steps;
    private LocalDateTime estimatedDeliveryAt;
    private LocalDateTime statusUpdatedAt;

    /** Header fields the tracking screen shows alongside the map/steps. */
    private String restaurantName;
    private int itemCount;
    private BigDecimal total;
}
