package com.food.foodapp.order.controller;

import com.food.foodapp.order.dto.AdminAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.AdminOrdersAnalyticsResponse;
import com.food.foodapp.order.dto.AdminOrdersByCityResponse;
import com.food.foodapp.order.dto.AdminRevenueAnalyticsResponse;
import com.food.foodapp.order.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Platform-wide admin analytics: the admin dashboard's default landing tab. Thin controller —
 * {@link AdminAnalyticsService} resolves periods/date ranges and computes every figure from
 * persisted order/restaurant/user data.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    /**
     * GET /api/v1/admin/analytics/overview?period=7d|30d|1y
     * The 4 KPI cards, each with a period-over-period trend. Defaults to {@code 7d} when omitted.
     */
    @GetMapping("/overview")
    public ResponseEntity<AdminAnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(adminAnalyticsService.getOverview(period));
    }

    /**
     * GET /api/v1/admin/analytics/orders
     * {@code from}/{@code to} (yyyy-MM-dd) must both be given or both omitted; omitting both
     * defaults to the current Monday-to-Sunday week — the admin dashboard's orders bar chart.
     */
    @GetMapping("/orders")
    public ResponseEntity<AdminOrdersAnalyticsResponse> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminAnalyticsService.getOrders(from, to));
    }

    /**
     * GET /api/v1/admin/analytics/revenue
     * Same {@code from}/{@code to} contract as {@link #getOrders}.
     */
    @GetMapping("/revenue")
    public ResponseEntity<AdminRevenueAnalyticsResponse> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminAnalyticsService.getRevenue(from, to));
    }

    /**
     * GET /api/v1/admin/analytics/orders-by-city
     * Same {@code from}/{@code to} contract as {@link #getOrders}. The admin-only orders-by-city
     * donut/legend.
     */
    @GetMapping("/orders-by-city")
    public ResponseEntity<AdminOrdersByCityResponse> getOrdersByCity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminAnalyticsService.getOrdersByCity(from, to));
    }
}
