package com.food.foodapp.address.controller;

import com.food.foodapp.address.dto.AddressResponse;
import com.food.foodapp.address.service.AddressService;
import com.food.foodapp.common.exception.AddressNotFoundException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @Test
    void listAddresses_returnsAddresses() throws Exception {
        when(addressService.listAddresses()).thenReturn(List.of(address()));

        mockMvc.perform(get("/api/v1/user/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].detail").value("Street 1، Cairo"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void listAddresses_returns401_whenUnauthenticated() throws Exception {
        when(addressService.listAddresses()).thenThrow(new UnauthenticatedException("Authentication required"));

        mockMvc.perform(get("/api/v1/user/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAddress_returns201() throws Exception {
        when(addressService.createAddress(any())).thenReturn(address());

        mockMvc.perform(post("/api/v1/user/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"Street 1\",\"city\":\"Cairo\",\"isDefault\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void createAddress_returns400_whenStreetMissing() throws Exception {
        mockMvc.perform(post("/api/v1/user/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Cairo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns400_whenCityMissing() throws Exception {
        mockMvc.perform(post("/api/v1/user/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"Street 1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_returnsUpdatedAddress() throws Exception {
        when(addressService.updateAddress(eq(100L), any())).thenReturn(address());

        mockMvc.perform(put("/api/v1/user/addresses/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"Street 1\",\"city\":\"Cairo\",\"isDefault\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateAddress_returns404_whenAddressMissingOrNotOwned() throws Exception {
        when(addressService.updateAddress(eq(999L), any()))
                .thenThrow(new AddressNotFoundException("Address not found: 999"));

        mockMvc.perform(put("/api/v1/user/addresses/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"Street 1\",\"city\":\"Cairo\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setDefaultAddress_returnsUpdatedAddress() throws Exception {
        when(addressService.setDefaultAddress(100L)).thenReturn(address());

        mockMvc.perform(patch("/api/v1/user/addresses/100/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void setDefaultAddress_returns404_whenAddressMissingOrNotOwned() throws Exception {
        when(addressService.setDefaultAddress(999L)).thenThrow(new AddressNotFoundException("Address not found: 999"));

        mockMvc.perform(patch("/api/v1/user/addresses/999/default"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAddress_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/user/addresses/100"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAddress_returns404_whenAddressMissingOrNotOwned() throws Exception {
        org.mockito.Mockito.doThrow(new AddressNotFoundException("Address not found: 999"))
                .when(addressService).deleteAddress(999L);

        mockMvc.perform(delete("/api/v1/user/addresses/999"))
                .andExpect(status().isNotFound());
    }

    private AddressResponse address() {
        return AddressResponse.builder()
                .id(100L)
                .label("Home")
                .street("Street 1")
                .city("Cairo")
                .detail("Street 1، Cairo")
                .isDefault(true)
                .build();
    }
}
