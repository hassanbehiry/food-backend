package com.food.foodapp.address.controller;

import com.food.foodapp.address.dto.AddressRequest;
import com.food.foodapp.address.dto.AddressResponse;
import com.food.foodapp.address.service.AddressService;
import com.food.foodapp.common.response.DeletionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer delivery-address endpoints, backing both the checkout address picker and
 * the profile "addresses" tab. Thin controller — {@link AddressService} resolves the
 * caller itself via {@code UserContext}. The frontend's {@code userService.js} drives
 * default-address changes through a full {@code PUT}; {@code PATCH .../default} is kept
 * as a convenience for a "set default" action that doesn't need to resend the whole
 * address.
 */
@RestController
@RequestMapping("/api/v1/user/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /** GET /api/v1/user/addresses */
    @GetMapping
    public ResponseEntity<List<AddressResponse>> listAddresses() {
        return ResponseEntity.ok(addressService.listAddresses());
    }

    /** POST /api/v1/user/addresses */
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.createAddress(request));
    }

    /** PUT /api/v1/user/addresses/{addressId} — full replace, may also change the default. */
    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable Long addressId, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(addressId, request));
    }

    /** PATCH /api/v1/user/addresses/{addressId}/default */
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(@PathVariable Long addressId) {
        return ResponseEntity.ok(addressService.setDefaultAddress(addressId));
    }

    /** DELETE /api/v1/user/addresses/{addressId} — 200 with {@code {deleted, promotedDefaultId?}}. */
    @DeleteMapping("/{addressId}")
    public ResponseEntity<DeletionResponse> deleteAddress(@PathVariable Long addressId) {
        return ResponseEntity.ok(addressService.deleteAddress(addressId));
    }
}
