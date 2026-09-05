package com.food.foodapp.address.service;

import com.food.foodapp.address.dto.AddressRequest;
import com.food.foodapp.address.dto.AddressResponse;
import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.mapper.AddressMapper;
import com.food.foodapp.address.repository.AddressRepository;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.AddressNotFoundException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import com.food.foodapp.common.response.DeletionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Customer delivery-address management: list/create/update/delete and default-address
 * selection. The caller is always resolved via {@link UserContext} — no method here
 * accepts a customer id from the caller — and every read/write of a specific address
 * goes through {@link AddressRepository#findByIdAndCustomerId}, so an id belonging to
 * another customer is indistinguishable from one that doesn't exist at all.
 * <p>
 * At most one address per customer may be the default. A cart has exactly one row per
 * customer, so its invariants can be protected with a unique constraint and a lock on
 * that one row; addresses don't have that (a customer can have zero, and the default
 * flag can move between any of them), so every mutation that can change the default
 * first takes {@link UserRepository#findByIdForUpdate} — a pessimistic lock on the
 * customer's user row, which always exists once authenticated, unlike any address row.
 * That serializes concurrent default-changing requests for the same customer, including
 * the case of two simultaneous "first address" creates racing to become the default.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final UserContext userContext;

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses() {
        Long customerId = userContext.getCurrentUserId();
        return addressRepository.findByCustomerId(customerId).stream()
                .map(AddressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(AddressRequest request) {
        Long customerId = lockCustomer();
        boolean isFirstAddress = addressRepository.findByCustomerId(customerId).isEmpty();

        Address address = new Address();
        address.setCustomer(userRepository.getReferenceById(customerId));
        applyRequest(address, request);
        // The very first saved address always becomes the default, regardless of what the
        // client sent, so checkout always has something to preselect once any address exists.
        address.setDefault(isFirstAddress || Boolean.TRUE.equals(request.getIsDefault()));

        Address saved = addressRepository.save(address);
        if (saved.isDefault()) {
            addressRepository.clearDefaultForOthers(customerId, saved.getId());
        }
        return AddressMapper.toResponse(saved);
    }

    /**
     * Partial with respect to the default flag: {@code isDefault} absent (null) leaves it
     * unchanged, so editing only the street of the current default address does not silently
     * clear it (GAP-013). An explicit {@code true}/{@code false} is applied as before.
     */
    @Transactional
    public AddressResponse updateAddress(Long addressId, AddressRequest request) {
        Long customerId = lockCustomer();
        Address address = requireOwnedAddress(addressId, customerId);

        applyRequest(address, request);
        if (request.getIsDefault() != null) {
            address.setDefault(request.getIsDefault());
        }

        Address saved = addressRepository.save(address);
        if (saved.isDefault()) {
            addressRepository.clearDefaultForOthers(customerId, saved.getId());
        }
        return AddressMapper.toResponse(saved);
    }

    @Transactional
    public AddressResponse setDefaultAddress(Long addressId) {
        Long customerId = lockCustomer();
        Address address = requireOwnedAddress(addressId, customerId);

        address.setDefault(true);
        Address saved = addressRepository.save(address);
        addressRepository.clearDefaultForOthers(customerId, saved.getId());
        return AddressMapper.toResponse(saved);
    }

    /**
     * @return {@code deleted:true} plus {@code promotedDefaultId} — the id of the address that was
     *         promoted to default because the deleted one had been the default, or {@code null} if
     *         nothing was promoted. The frontend service layer reads the response body, so this is
     *         a 200 with a body rather than a bare 204.
     */
    @Transactional
    public DeletionResponse deleteAddress(Long addressId) {
        Long customerId = lockCustomer();
        Address address = requireOwnedAddress(addressId, customerId);
        boolean wasDefault = address.isDefault();

        addressRepository.delete(address);

        // Deleting the default address promotes the customer's oldest remaining address to
        // default, so checkout keeps a preselectable address whenever one is available — there
        // is no prior convention in this codebase for this case, so this is the assumption made.
        Long promotedDefaultId = null;
        if (wasDefault) {
            Optional<Address> promoted = addressRepository.findFirstByCustomerIdOrderByCreatedAtAsc(customerId);
            if (promoted.isPresent()) {
                Address next = promoted.get();
                next.setDefault(true);
                addressRepository.save(next);
                promotedDefaultId = next.getId();
            }
        }
        return DeletionResponse.ok(promotedDefaultId);
    }

    private Address requireOwnedAddress(Long addressId, Long customerId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new AddressNotFoundException("Address not found: " + addressId));
    }

    private void applyRequest(Address address, AddressRequest request) {
        address.setLabel(request.getLabel());
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setPostalCode(request.getPostalCode());
        address.setNotes(request.getNotes());
    }

    private Long lockCustomer() {
        Long customerId = userContext.getCurrentUserId();
        userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new UnauthenticatedException("Authentication required"));
        return customerId;
    }
}
