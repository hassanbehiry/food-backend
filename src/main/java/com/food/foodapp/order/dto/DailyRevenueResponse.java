package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One bar of the admin dashboard's revenue chart: one calendar day, zero-filled when no order was delivered that day. */
@Getter
@Builder
public class DailyRevenueResponse {

    private LocalDate date;
    private BigDecimal revenue;
    private long orderCount;
}
