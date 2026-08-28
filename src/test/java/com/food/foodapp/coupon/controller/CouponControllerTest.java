package com.food.foodapp.coupon.controller;

import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.CouponNotApplicableException;
import com.food.foodapp.common.exception.CouponNotFoundException;
import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.entity.DiscountType;
import com.food.foodapp.coupon.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    @Test
    void validate_returnsComputedPreview_whenCouponApplicable() throws Exception {
        when(couponService.validateForCurrentCart("SAVE10")).thenReturn(preview());

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE10"))
                .andExpect(jsonPath("$.discount").value(10))
                .andExpect(jsonPath("$.total").value(102));
    }

    @Test
    void validate_returns400_whenCodeMissing() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_returns404_whenCouponUnknown() throws Exception {
        when(couponService.validateForCurrentCart("BADCODE"))
                .thenThrow(new CouponNotFoundException("Coupon not found: BADCODE"));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BADCODE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validate_returns409_whenCouponNotApplicable() throws Exception {
        when(couponService.validateForCurrentCart("EXPIRED"))
                .thenThrow(new CouponNotApplicableException("Coupon has expired: EXPIRED"));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EXPIRED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void validate_returns409_whenCartEmpty() throws Exception {
        when(couponService.validateForCurrentCart(any())).thenThrow(new CartEmptyException("Cart is empty"));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE10\"}"))
                .andExpect(status().isConflict());
    }

    private CouponService.CouponApplicationPreview preview() {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        coupon.setDiscountType(DiscountType.FIXED);
        coupon.setDiscountValue(BigDecimal.TEN);
        CouponService.CouponApplication application =
                new CouponService.CouponApplication(coupon, BigDecimal.TEN);
        return new CouponService.CouponApplicationPreview(application, BigDecimal.valueOf(100), BigDecimal.valueOf(12));
    }
}
