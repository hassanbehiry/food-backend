package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One {@code RevenueTransaction} ledger row, as returned alongside the order in {@link OrderDeliveryResponse}. */
@Getter
@Builder
public class RevenueResponse {

    private BigDecimal amount;
    private LocalDateTime createdAt;
}
