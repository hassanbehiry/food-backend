package com.food.foodapp.address.mapper;

import com.food.foodapp.address.dto.AddressResponse;
import com.food.foodapp.address.entity.Address;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AddressMapper {

    private AddressMapper() {
    }

    public static AddressResponse toResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .street(address.getStreet())
                .city(address.getCity())
                .postalCode(address.getPostalCode())
                .notes(address.getNotes())
                .detail(composeDetail(address))
                .isDefault(address.isDefault())
                .build();
    }

    /** street, city, postalCode joined into the single line the checkout picker and profile list render. */
    public static String composeDetail(Address address) {
        return composeDetail(address.getStreet(), address.getCity(), address.getPostalCode());
    }

    /**
     * Same composition as {@link #composeDetail(Address)}, from raw parts — used by
     * {@code OrderMapper} to render an order's snapshotted delivery fields, which are plain
     * columns rather than a live {@link Address}, into the same single-line format.
     */
    public static String composeDetail(String street, String city, String postalCode) {
        return Stream.of(street, city, postalCode)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining("، "));
    }
}
