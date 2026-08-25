package com.food.foodapp.address.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Full address projection. {@code detail} is the composed single-line rendering that
 * both the checkout address picker and the profile addresses list actually display
 * (alongside {@code label} and {@code isDefault}); the structured fields are included
 * too so the same object can prefill an edit form without a second round-trip.
 */
@Getter
@Builder
public class AddressResponse {

    private Long id;
    private String label;
    private String street;
    private String city;
    private String postalCode;
    private String notes;
    private String detail;

    /** See {@link AddressRequest#isDefault} for why this needs an explicit {@code @JsonProperty}. */
    @Getter(onMethod_ = @JsonProperty("isDefault"))
    private boolean isDefault;
}
