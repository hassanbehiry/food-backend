package com.food.foodapp.cart.mapper;

import com.food.foodapp.cart.dto.CartItemResponse;
import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

public final class CartMapper {

    private CartMapper() {
    }

    public static CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartMapper::toItemResponse)
                .toList();

        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = cart.getRestaurant() != null ? cart.getRestaurant().getDeliveryFee() : BigDecimal.ZERO;
        // Placeholder — coupon/discount engine is a separate task (phase-2-customer-engagement/11-coupon-discount.md).
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee).subtract(discount);

        return CartResponse.builder()
                .id(cart.getId())
                .restaurantId(cart.getRestaurant() != null ? cart.getRestaurant().getId() : null)
                .restaurantName(cart.getRestaurant() != null ? cart.getRestaurant().getName() : null)
                .items(items)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .discount(discount)
                .total(total)
                .build();
    }

    private static CartItemResponse toItemResponse(CartItem item) {
        BigDecimal price = item.getMenuItem().getPrice();
        BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

        return CartItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItem().getId())
                .name(item.getMenuItem().getName())
                .img(item.getMenuItem().getImageUrl())
                .price(price)
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }
}
