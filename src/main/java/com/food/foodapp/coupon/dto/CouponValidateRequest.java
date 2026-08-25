package com.food.foodapp.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponValidateRequest {

    @NotBlank(message = "code is required")
    private String code;
}
