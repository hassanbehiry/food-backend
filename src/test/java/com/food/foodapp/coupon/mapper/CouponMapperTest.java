package com.food.foodapp.coupon.mapper;

import com.food.foodapp.coupon.dto.CouponValidationResponse;
import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.entity.DiscountType;
import com.food.foodapp.coupon.service.CouponService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CouponMapperTest {

    @Test
    void toValidationResponse_computesTotal_asSubtotalPlusDeliveryFeeMinusDiscount() {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        coupon.setDiscountType(DiscountType.FIXED);
        coupon.setDiscountValue(BigDecimal.TEN);
        CouponService.CouponApplication application = new CouponService.CouponApplication(coupon, BigDecimal.TEN);
        CouponService.CouponApplicationPreview preview =
                new CouponService.CouponApplicationPreview(application, BigDecimal.valueOf(100), BigDecimal.valueOf(12));

        CouponValidationResponse response = CouponMapper.toValidationResponse(preview);

        assertThat(response.getCode()).isEqualTo("SAVE10");
        assertThat(response.getDiscountType()).isEqualTo(DiscountType.FIXED);
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(102));
    }
}
