package com.food.foodapp.order.dto;

import com.food.foodapp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One row of the owner dashboard's Delivery Orders table (see {@code OrderService#getDeliveryDashboard}).
 * Deliberately its own type rather than reusing {@link OwnerOrderSummaryResponse}: the delivery
 * queue is always small (a restaurant's currently-{@code OUT_FOR_DELIVERY} orders, never its whole
 * history — see {@code OrderRepository#findByRestaurantIdAndStatusWithItems}), so unlike the
 * paginated general order list, it can afford to carry the full item breakdown and delivery contact
 * details every row of that table needs.
 */
@Getter
@Builder
public class DeliveryOrderResponse {

    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private List<OrderItemResponse> items;
    private BigDecimal total;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime sentToDeliveryAt;
    private String deliveryPersonName;
}
