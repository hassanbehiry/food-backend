package com.food.foodapp.cart.mapper;

import com.food.foodapp.cart.dto.CartItemResponse;
import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CartMapperTest {

    @Test
    void toResponse_computesSubtotalDeliveryFeeAndTotal_fromLiveMenuItemPrices() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setDeliveryFee(BigDecimal.valueOf(12));

        Cart cart = new Cart();
        cart.setId(100L);
        cart.setRestaurant(restaurant);
        cart.setItems(List.of(
                cartItem(1L, menuItem(10L, "مارجريتا", BigDecimal.valueOf(50)), 2),
                cartItem(2L, menuItem(11L, "كولا", BigDecimal.valueOf(10)), 3)));

        CartResponse response = CartMapper.toResponse(cart);

        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(130)); // (50*2)+(10*3)
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(142));
        assertThat(response.getRestaurantId()).isEqualTo(1L);
    }

    @Test
    void toResponse_usesZeroDeliveryFeeAndNullRestaurant_whenCartIsEmpty() {
        Cart cart = new Cart();
        cart.setId(100L);

        CartResponse response = CartMapper.toResponse(cart);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getRestaurantId()).isNull();
    }

    @Test
    void toResponse_derivesItemPriceAndLineTotal_fromCurrentMenuItemPrice() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setItems(List.of(cartItem(1L, menuItem(10L, "مارجريتا", BigDecimal.valueOf(50)), 3)));

        CartItemResponse item = CartMapper.toResponse(cart).getItems().get(0);

        assertThat(item.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(item.getLineTotal()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    private CartItem cartItem(Long id, MenuItem menuItem, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setMenuItem(menuItem);
        item.setQuantity(quantity);
        return item;
    }

    private MenuItem menuItem(Long id, String name, BigDecimal price) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(id);
        menuItem.setName(name);
        menuItem.setPrice(price);
        return menuItem;
    }
}
