package com.food.foodapp.order.dto;

import lombok.Getter;
import lombok.Setter;

/** Body of {@code POST /owner/restaurants/{restaurantId}/orders/{orderId}/send-to-delivery}. */
@Getter
@Setter
public class SendToDeliveryRequest {

    /** Optional free-text courier name/label — see {@code Order#getDeliveryPersonName()}. */
    private String deliveryPersonName;
}
