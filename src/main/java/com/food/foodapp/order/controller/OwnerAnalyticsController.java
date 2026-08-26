package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.service.OrderAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Restaurant-owner analytics: the dashboard overview's KPI cards and its revenue chart. Thin
 * controller — {@link OrderAnalyticsService} resolves date ranges, enforces restaurant scoping,
 * and computes every figure from persisted order data.
 */
@RestController
@RequestMapping("/api/v1/owner/restaurants/{restaurantId}/analytics")
@RequiredArgsConstructor
public class OwnerAnalyticsController {

    private final OrderAnalyticsService orderAnalyticsService;

    /** GET /api/v1/owner/restaurants/{restaurantId}/analytics/overview — always the current calendar month. */
    @GetMapping("/overview")
    public ResponseEntity<OwnerAnalyticsOverviewResponse> getOverview(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(orderAnalyticsService.getOverview(restaurantId));
    }

    /**
     * GET /api/v1/owner/restaurants/{restaurantId}/analytics/revenue
     * {@code from}/{@code to} (yyyy-MM-dd) must both be given or both omitted; omitting both
     * defaults to the current Saturday-to-Friday week.
     */
    @GetMapping("/revenue")
    public ResponseEntity<OwnerRevenueAnalyticsResponse> getRevenue(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(orderAnalyticsService.getRevenue(restaurantId, from, to));
    }
}
