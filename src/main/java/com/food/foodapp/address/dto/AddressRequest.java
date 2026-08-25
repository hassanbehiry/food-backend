package com.food.foodapp.address.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared shape for both create ({@code POST}) and full update ({@code PUT}) of a
 * saved address. The frontend's checkout inline form and profile "add address" modal
 * collect different subsets of these fields (see the address-management task notes) —
 * this accepts the union of both, requiring only {@code street}/{@code city} since
 * those are the two fields both existing forms already collect.
 */
@Getter
@Setter
public class AddressRequest {

    @Size(max = 100, message = "label must be at most 100 characters")
    private String label;

    @NotBlank(message = "street is required")
    @Size(max = 255, message = "street must be at most 255 characters")
    private String street;

    @NotBlank(message = "city is required")
    @Size(max = 100, message = "city must be at most 100 characters")
    private String city;

    @Size(max = 20, message = "postalCode must be at most 20 characters")
    private String postalCode;

    @Size(max = 500, message = "notes must be at most 500 characters")
    private String notes;

    /**
     * Lombok's boolean-getter convention would otherwise generate {@code isDefault()}/
     * {@code setDefault(boolean)}, which Jackson binds to the JSON key "default" instead of
     * "isDefault" — the key the frontend's address shape actually uses. The explicit
     * {@code @JsonProperty} on the generated accessors keeps the wire name correct
     * regardless of what Lombok names the underlying Java methods.
     */
    @Getter(onMethod_ = @JsonProperty("isDefault"))
    @Setter(onMethod_ = @JsonProperty("isDefault"))
    private boolean isDefault;
}
