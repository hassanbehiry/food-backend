package com.food.foodapp.order.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * The response of {@code POST /owner/restaurants/{restaurantId}/orders/{orderId}/deliver}: the
 * now-{@code DELIVERED} order plus the {@code RevenueTransaction} row recognized in the same
 * database transaction (see {@code OrderService#deliverOrder}). A {@code 200} response here always
 * means both writes committed — there is no partial-success shape.
 */
@Getter
@Builder
public class OrderDeliveryResponse {

    private OwnerOrderResponse order;
    private RevenueResponse revenue;
}
