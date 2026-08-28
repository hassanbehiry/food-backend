package com.food.foodapp.order.mapper;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.mapper.AddressMapper;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.cart.mapper.CartMapper;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.dto.OrderItemResponse;
import com.food.foodapp.order.dto.OrderResponse;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.restaurant.entity.Restaurant;

import java.math.BigDecimal;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static CheckoutResponse toCheckoutResponse(Restaurant restaurant, List<CartItem> items, Address address,
                                                        PaymentMethod paymentMethod, BigDecimal subtotal,
                                                        BigDecimal deliveryFee, BigDecimal discount, BigDecimal total) {
        return CheckoutResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .items(items.stream().map(CartMapper::toItemResponse).toList())
                .addressId(address.getId())
                .deliveryAddress(AddressMapper.composeDetail(address))
                .paymentMethod(paymentMethod)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .discount(discount)
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
                .discount(order.getDiscount())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
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
