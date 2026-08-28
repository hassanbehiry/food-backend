package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One slice of the admin dashboard's "orders by city" donut: a city name (or the literal
 * {@code "Other"} bucket — see {@code AdminAnalyticsService#getOrdersByCity}), its order count,
 * and its share of the period's total orders as a percentage.
 */
@Getter
@Builder
public class CityOrderShareResponse {

    private String city;
    private long orderCount;
    private BigDecimal percentage;
}
