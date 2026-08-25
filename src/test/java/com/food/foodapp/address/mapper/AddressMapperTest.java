package com.food.foodapp.address.mapper;

import com.food.foodapp.address.dto.AddressResponse;
import com.food.foodapp.address.entity.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressMapperTest {

    @Test
    void toResponse_composesDetail_fromStreetCityAndPostalCode() {
        Address address = address("Home", "12 شارع التحرير", "الجيزة", "12611", "leave at door", false);

        AddressResponse response = AddressMapper.toResponse(address);

        assertThat(response.getDetail()).isEqualTo("12 شارع التحرير، الجيزة، 12611");
        assertThat(response.getLabel()).isEqualTo("Home");
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    void toResponse_omitsBlankPostalCode_fromDetail() {
        Address address = address("Work", "Street 1", "Cairo", null, null, true);

        AddressResponse response = AddressMapper.toResponse(address);

        assertThat(response.getDetail()).isEqualTo("Street 1، Cairo");
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void toResponse_carriesStructuredFields_forEditFormPrefill() {
        Address address = address("Home", "Street 1", "Cairo", "11511", "ring twice", false);

        AddressResponse response = AddressMapper.toResponse(address);

        assertThat(response.getStreet()).isEqualTo("Street 1");
        assertThat(response.getCity()).isEqualTo("Cairo");
        assertThat(response.getPostalCode()).isEqualTo("11511");
        assertThat(response.getNotes()).isEqualTo("ring twice");
    }

    private Address address(String label, String street, String city, String postalCode, String notes, boolean isDefault) {
        Address address = new Address();
        address.setId(1L);
        address.setLabel(label);
        address.setStreet(street);
        address.setCity(city);
        address.setPostalCode(postalCode);
        address.setNotes(notes);
        address.setDefault(isDefault);
        return address;
    }
}
