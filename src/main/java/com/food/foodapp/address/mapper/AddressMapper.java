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
    private static String composeDetail(Address address) {
        return Stream.of(address.getStreet(), address.getCity(), address.getPostalCode())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining("، "));
    }
}
