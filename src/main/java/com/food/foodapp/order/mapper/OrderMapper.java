package com.food.foodapp.order.mapper;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.mapper.AddressMapper;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.cart.mapper.CartMapper;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.DeliveryDashboardResponse;
import com.food.foodapp.order.dto.DeliveryDashboardSummaryResponse;
import com.food.foodapp.order.dto.DeliveryOrderResponse;
import com.food.foodapp.order.dto.OrderDeliveryResponse;
import com.food.foodapp.order.dto.OrderItemResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.dto.OrderSummaryResponse;
import com.food.foodapp.order.dto.OrderTrackingResponse;
import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerDashboardResponse;
import com.food.foodapp.order.dto.OwnerOrderResponse;
import com.food.foodapp.order.dto.OwnerOrderStatsResponse;
import com.food.foodapp.order.dto.OwnerOrderSummaryResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.dto.RevenueResponse;
import com.food.foodapp.order.dto.TrackingStepResponse;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.entity.RevenueTransaction;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.restaurant.entity.Restaurant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class OrderMapper {

    /** The customer-visible tracking milestones, in forward order — see {@link OrderStatus}. */
    private static final List<OrderStatus> TRACKING_MILESTONES = List.of(
            OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_DELIVERY,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private OrderMapper() {
    }

    public static CheckoutResponse toCheckoutResponse(Restaurant restaurant, List<CartItem> items, Address address,
                                                        PaymentMethod paymentMethod, BigDecimal subtotal,
                                                        BigDecimal deliveryFee, BigDecimal total) {
        return CheckoutResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .items(items.stream().map(CartMapper::toItemResponse).toList())
                .addressId(address.getId())
                .deliveryAddress(AddressMapper.composeDetail(address))
                .paymentMethod(paymentMethod)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .total(total)
                .build();
    }

    public static OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .restaurantId(order.getRestaurant().getId())
                .restaurantName(order.getRestaurant().getName())
                .items(order.getItems().stream().map(OrderMapper::toItemResponse).toList())
                .deliveryAddress(AddressMapper.composeDetail(
                        order.getDeliveryStreet(), order.getDeliveryCity(), order.getDeliveryPostalCode()))
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .deliveredAt(order.getDeliveredAt())
                .build();
    }

    /**
     * {@code steps} is derived purely from {@code order.getStatus()} against
     * {@link #TRACKING_MILESTONES}: a {@code CANCELLED} order shows no step as completed or
     * current, since {@code status} on the response already tells the caller it was cancelled.
     */
    public static OrderTrackingResponse toTracking(Order order) {
        OrderStatus status = order.getStatus();
        int currentRank = milestoneRank(status);

        List<TrackingStepResponse> steps = new ArrayList<>();
        for (int i = 0; i < TRACKING_MILESTONES.size(); i++) {
            steps.add(TrackingStepResponse.builder()
                    .status(TRACKING_MILESTONES.get(i))
                    .completed(currentRank >= 0 && i <= currentRank)
                    .current(currentRank >= 0 && i == currentRank)
                    .build());
        }

        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();

        return OrderTrackingResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(status)
                .steps(steps)
                .estimatedDeliveryAt(estimateDeliveryAt(order))
                .statusUpdatedAt(order.getUpdatedAt())
                .deliveredAt(order.getDeliveredAt())
                .restaurantName(order.getRestaurant().getName())
                .itemCount(itemCount)
                .total(order.getTotal())
                .build();
    }

    /** One row of the customer's order-history table — see {@link OrderSummaryResponse}. */
    public static OrderSummaryResponse toSummary(Order order, long itemCount) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .restaurantId(order.getRestaurant().getId())
                .restaurantName(order.getRestaurant().getName())
                .itemCount((int) itemCount)
                .total(order.getTotal())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * One row of the owner dashboard's orders table — see {@link OwnerOrderSummaryResponse}.
     * {@code itemCount} is the order's total item quantity, resolved by the caller in one batch
     * query for the whole page (see {@code OrderRepository#sumItemQuantitiesByOrderIds}), the same
     * way {@link #toSummary} takes it for the customer's history table.
     */
    public static OwnerOrderSummaryResponse toOwnerSummary(Order order, long itemCount) {
        return OwnerOrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomer().getName())
                .itemCount((int) itemCount)
                .total(order.getTotal())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /** The owner order-detail view — {@link #toResponse} plus the customer's display name. */
    public static OwnerOrderResponse toOwnerResponse(Order order) {
        return OwnerOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomer().getName())
                .items(order.getItems().stream().map(OrderMapper::toItemResponse).toList())
                .deliveryAddress(AddressMapper.composeDetail(
                        order.getDeliveryStreet(), order.getDeliveryCity(), order.getDeliveryPostalCode()))
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .deliveredAt(order.getDeliveredAt())
                .deliveredBy(order.getDeliveredBy())
                .sentToDeliveryAt(order.getSentToDeliveryAt())
                .deliveryPersonName(order.getDeliveryPersonName())
                .build();
    }

    /** The combined order+revenue payload of {@code POST .../orders/{id}/deliver} — see {@code OrderService#deliverOrder}. */
    public static OrderDeliveryResponse toOrderDeliveryResponse(Order order, RevenueTransaction revenue) {
        return OrderDeliveryResponse.builder()
                .order(toOwnerResponse(order))
                .revenue(RevenueResponse.builder()
                        .amount(revenue.getAmount())
                        .createdAt(revenue.getCreatedAt())
                        .build())
                .build();
    }

    /** One row of the delivery dashboard's orders table — see {@link DeliveryOrderResponse}. */
    public static DeliveryOrderResponse toDeliveryOrderResponse(Order order) {
        return DeliveryOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomer().getName())
                .customerPhone(order.getCustomer().getPhone())
                .deliveryAddress(AddressMapper.composeDetail(
                        order.getDeliveryStreet(), order.getDeliveryCity(), order.getDeliveryPostalCode()))
                .items(order.getItems().stream().map(OrderMapper::toItemResponse).toList())
                .total(order.getTotal())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .sentToDeliveryAt(order.getSentToDeliveryAt())
                .deliveryPersonName(order.getDeliveryPersonName())
                .build();
    }

    /** The delivery dashboard's summary cards + orders table — see {@code OrderService#getDeliveryDashboard}. */
    public static DeliveryDashboardResponse toDeliveryDashboard(List<Order> activeOrders, RevenueAggregate deliveredToday) {
        BigDecimal activeTotalValue = activeOrders.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DeliveryDashboardSummaryResponse summary = DeliveryDashboardSummaryResponse.builder()
                .activeCount(activeOrders.size())
                .activeTotalValue(activeTotalValue)
                .deliveredTodayCount(deliveredToday.orderCount())
                .deliveredTodayRevenue(deliveredToday.totalRevenueOrZero())
                .build();

        return DeliveryDashboardResponse.builder()
                .summary(summary)
                .orders(activeOrders.stream().map(OrderMapper::toDeliveryOrderResponse).toList())
                .build();
    }

    /**
     * The owner dashboard's landing view. {@code overview} and {@code revenue} are the payloads
     * {@code OrderAnalyticsService} already computes for the {@code /analytics/overview} and
     * {@code /analytics/revenue} endpoints ({@code revenue} being the no-range default: the
     * current Saturday-to-Friday week with a week-over-week change) — the dashboard reuses those
     * figures verbatim rather than re-deriving any aggregation.
     */
    public static OwnerDashboardResponse toDashboard(Restaurant restaurant, OwnerOrderStatsResponse stats,
                                                       List<OwnerOrderSummaryResponse> recentOrders,
                                                       OwnerAnalyticsOverviewResponse overview,
                                                       OwnerRevenueAnalyticsResponse revenue) {
        return OwnerDashboardResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .stats(stats)
                .recentOrders(recentOrders)
                .monthOrders(overview.getTotalOrders())
                .monthOrdersTrendPct(overview.getTotalOrdersTrendPercentage())
                .monthRevenue(overview.getRevenue())
                .monthRevenueTrendPct(overview.getRevenueTrendPercentage())
                .last7DaysRevenue(revenue.getDailyRevenue())
                .weekOverWeekPct(revenue.getChangePercentage())
                .build();
    }

    private static int milestoneRank(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> 0;
            case PREPARING -> 1;
            case READY_FOR_DELIVERY -> 2;
            case OUT_FOR_DELIVERY -> 3;
            case DELIVERED -> 4;
            case CANCELLED -> -1;
        };
    }

    private static LocalDateTime estimateDeliveryAt(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            return null;
        }
        Restaurant restaurant = order.getRestaurant();
        return order.getCreatedAt().plusMinutes(restaurant.getEstimatedDeliveryMaxMinutes());
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItemId())
                .name(item.getName())
                .img(item.getImageUrl())
                .price(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .build();
    }
}
