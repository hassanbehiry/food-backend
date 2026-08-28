package com.food.foodapp.order.controller;

import com.food.foodapp.cart.dto.CartItemResponse;
import com.food.foodapp.common.exception.AddressNotFoundException;
import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.MenuItemUnavailableException;
import com.food.foodapp.order.dto.CheckoutResponse;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void checkout_returnsComputedPreview() throws Exception {
        when(orderService.previewCheckout(any())).thenReturn(previewResponse());

        mockMvc.perform(post("/api/v1/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":50,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(112))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void checkout_returns400_whenAddressIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_returns409_whenCartEmpty() throws Exception {
        when(orderService.previewCheckout(any())).thenThrow(new CartEmptyException("Cart is empty"));

        mockMvc.perform(post("/api/v1/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":50,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void checkout_returns409_whenItemUnavailable() throws Exception {
        when(orderService.previewCheckout(any()))
                .thenThrow(new MenuItemUnavailableException("Menu item 10 is not currently available"));

        mockMvc.perform(post("/api/v1/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":50,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void checkout_returns404_whenAddressNotOwned() throws Exception {
        when(orderService.previewCheckout(any())).thenThrow(new AddressNotFoundException("Address not found: 999"));

        mockMvc.perform(post("/api/v1/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":999,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"))
                .andExpect(status().isNotFound());
    }

    private CheckoutResponse previewResponse() {
        return CheckoutResponse.builder()
                .restaurantId(5L)
                .restaurantName("Pizza Place")
                .items(List.of(CartItemResponse.builder()
                        .id(1L)
                        .menuItemId(10L)
                        .name("Pizza")
                        .price(BigDecimal.valueOf(50))
                        .quantity(2)
                        .lineTotal(BigDecimal.valueOf(100))
                        .build()))
                .addressId(50L)
                .deliveryAddress("Street 1، Cairo")
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .subtotal(BigDecimal.valueOf(100))
                .deliveryFee(BigDecimal.valueOf(12))
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(112))
                .build();
    }
}
