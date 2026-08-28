package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * The admin dashboard's orders-by-city donut/legend payload for an arbitrary {@code [from, to]}
 * range: every delivery city's share of the period's orders, with the long tail beyond the top
 * cities collapsed into a single {@code "Other"} entry — see
 * {@code AdminAnalyticsService#getOrdersByCity}. Defaults to the current Monday-to-Sunday week
 * when {@code from}/{@code to} are omitted, the same as {@link AdminOrdersAnalyticsResponse} and
 * {@link AdminRevenueAnalyticsResponse}.
 */
@Getter
@Builder
public class AdminOrdersByCityResponse {

    private LocalDate from;
    private LocalDate to;

    private long totalOrders;
    private List<CityOrderShareResponse> cities;
}
