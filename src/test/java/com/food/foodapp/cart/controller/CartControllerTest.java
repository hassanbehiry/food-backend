package com.food.foodapp.cart.controller;

import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.service.CartService;
import com.food.foodapp.common.exception.CartItemNotFoundException;
import com.food.foodapp.common.exception.CartRestaurantConflictException;
import com.food.foodapp.common.exception.MenuItemUnavailableException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void getCart_returnsCart() throws Exception {
        when(cartService.getCart()).thenReturn(emptyCart());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getCart_returns401_whenUnauthenticated() throws Exception {
        when(cartService.getCart()).thenThrow(new UnauthenticatedException("Authentication required"));

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sync_returnsRecalculatedCart() throws Exception {
        when(cartService.syncCart(any())).thenReturn(cartWithOneItem());

        mockMvc.perform(post("/api/v1/cart/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"menuItemId\":10,\"qty\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void sync_returns400_whenItemsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/cart/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_returns201() throws Exception {
        when(cartService.addItem(any())).thenReturn(cartWithOneItem());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":10,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void addItem_returns409_whenItemUnavailable() throws Exception {
        when(cartService.addItem(any()))
                .thenThrow(new MenuItemUnavailableException("Menu item 10 is not currently available"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":10,\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_returns409_whenRestaurantConflict() throws Exception {
        when(cartService.addItem(any()))
                .thenThrow(new CartRestaurantConflictException("Cart already contains items from a different restaurant"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":10,\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_returns400_whenQuantityMissing() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateItemQuantity_returnsUpdatedCart() throws Exception {
        when(cartService.updateItemQuantity(eq(500L), any())).thenReturn(cartWithOneItem());

        mockMvc.perform(patch("/api/v1/cart/items/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateItemQuantity_returns404_whenItemMissing() throws Exception {
        when(cartService.updateItemQuantity(eq(999L), any()))
                .thenThrow(new CartItemNotFoundException("Cart item not found: 999"));

        mockMvc.perform(patch("/api/v1/cart/items/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_returnsUpdatedCart() throws Exception {
        when(cartService.removeItem(500L)).thenReturn(emptyCart());

        mockMvc.perform(delete("/api/v1/cart/items/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void clearCart_returnsEmptyCart() throws Exception {
        when(cartService.clearCart()).thenReturn(emptyCart());

        mockMvc.perform(delete("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private CartResponse emptyCart() {
        return CartResponse.builder()
                .id(1L)
                .items(List.of())
                .subtotal(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();
    }

    private CartResponse cartWithOneItem() {
        return CartResponse.builder()
                .id(1L)
                .restaurantId(5L)
                .items(List.of(com.food.foodapp.cart.dto.CartItemResponse.builder()
                        .id(500L)
                        .menuItemId(10L)
                        .name("مارجريتا")
                        .price(BigDecimal.valueOf(50))
                        .quantity(2)
                        .lineTotal(BigDecimal.valueOf(100))
                        .build()))
                .subtotal(BigDecimal.valueOf(100))
                .deliveryFee(BigDecimal.valueOf(12))
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(112))
                .build();
    }
}
