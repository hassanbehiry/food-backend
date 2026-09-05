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
     * Boxed so the service can tell "make/keep this the default" ({@code true}), "no longer the
     * default" ({@code false}), and "not mentioned — leave the flag as it is" ({@code null})
     * apart: a {@code PUT} that edits only the street must not silently drop the customer's
     * default. On {@code POST} a missing value is treated as {@code false}.
     * <p>
     * Explicit accessors (Lombok's class-level {@code @Getter}/{@code @Setter} skip a field that
     * already has them) so the wire name stays {@code isDefault} regardless of Jackson's
     * {@code Boolean}-getter naming.
     */
    @JsonProperty("isDefault")
    private Boolean isDefault;

    @JsonProperty("isDefault")
    public Boolean getIsDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
}
