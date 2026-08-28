package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** One bar of the admin dashboard's orders chart: one calendar day, zero-filled when no order was placed that day. */
@Getter
@Builder
public class DailyOrderCountResponse {

    private LocalDate date;
    private long orderCount;
}
