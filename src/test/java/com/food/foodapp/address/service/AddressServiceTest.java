package com.food.foodapp.address.service;

import com.food.foodapp.address.dto.AddressRequest;
import com.food.foodapp.address.dto.AddressResponse;
import com.food.foodapp.address.entity.Address;
import com.food.foodapp.address.repository.AddressRepository;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.AddressNotFoundException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserContext userContext;

    private AddressService addressService;

    @BeforeEach
    void setUp() {
        addressService = new AddressService(addressRepository, userRepository, userContext);
        when(userContext.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void listAddresses_returnsMappedAddresses_orderedByRepository() {
        Address address = existingAddress(100L, true);
        when(addressRepository.findByCustomerId(1L)).thenReturn(List.of(address));

        List<AddressResponse> responses = addressService.listAddresses();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(100L);
        assertThat(responses.get(0).isDefault()).isTrue();
    }

    @Test
    void createAddress_becomesDefault_whenItIsTheCustomersFirstAddress() {
        stubLock();
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(addressRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(addressRepository.save(any(Address.class))).thenAnswer(answerWithGeneratedId(200L));

        AddressResponse response = addressService.createAddress(request("Home", "Street 1", "Cairo", false));

        assertThat(response.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForOthers(1L, 200L);
    }

    @Test
    void createAddress_staysNonDefault_whenNotRequestedAndOtherAddressesExist() {
        stubLock();
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(addressRepository.findByCustomerId(1L)).thenReturn(List.of(existingAddress(100L, true)));
        when(addressRepository.save(any(Address.class))).thenAnswer(answerWithGeneratedId(201L));

        AddressResponse response = addressService.createAddress(request("Work", "Street 2", "Giza", false));

        assertThat(response.isDefault()).isFalse();
        verify(addressRepository, never()).clearDefaultForOthers(any(), any());
    }

    @Test
    void createAddress_clearsOtherDefaults_whenExplicitlyRequestedAsDefault() {
        stubLock();
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(addressRepository.findByCustomerId(1L)).thenReturn(List.of(existingAddress(100L, true)));
        when(addressRepository.save(any(Address.class))).thenAnswer(answerWithGeneratedId(201L));

        addressService.createAddress(request("Work", "Street 2", "Giza", true));

        verify(addressRepository).clearDefaultForOthers(1L, 201L);
    }

    @Test
    void updateAddress_throwsNotFound_whenAddressMissingOrNotOwned() {
        stubLock();
        when(addressRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(999L, request("Home", "Street 1", "Cairo", false)))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void updateAddress_updatesFieldsAndClearsOtherDefaults_whenRequestedAsDefault() {
        stubLock();
        Address address = existingAddress(100L, false);
        when(addressRepository.findByIdAndCustomerId(100L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(100L, request("New Label", "New Street", "New City", true));

        assertThat(response.getLabel()).isEqualTo("New Label");
        assertThat(response.getStreet()).isEqualTo("New Street");
        assertThat(response.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForOthers(1L, 100L);
    }

    @Test
    void updateAddress_leavesOtherDefaultsAlone_whenNotRequestedAsDefault() {
        stubLock();
        Address address = existingAddress(100L, true);
        when(addressRepository.findByIdAndCustomerId(100L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.updateAddress(100L, request("Home", "Street 1", "Cairo", false));

        assertThat(response.isDefault()).isFalse();
        verify(addressRepository, never()).clearDefaultForOthers(any(), any());
    }

    @Test
    void setDefaultAddress_setsDefaultAndClearsOthers() {
        stubLock();
        Address address = existingAddress(100L, false);
        when(addressRepository.findByIdAndCustomerId(100L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.setDefaultAddress(100L);

        assertThat(response.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForOthers(1L, 100L);
    }

    @Test
    void setDefaultAddress_throwsNotFound_whenAddressMissingOrNotOwned() {
        stubLock();
        when(addressRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.setDefaultAddress(999L)).isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void deleteAddress_deletesWithoutPromotion_whenAddressWasNotDefault() {
        stubLock();
        Address address = existingAddress(100L, false);
        when(addressRepository.findByIdAndCustomerId(100L, 1L)).thenReturn(Optional.of(address));

        addressService.deleteAddress(100L);

        verify(addressRepository).delete(address);
        verify(addressRepository, never()).findFirstByCustomerIdOrderByCreatedAtAsc(any());
    }

    @Test
    void deleteAddress_promotesOldestRemaining_whenDeletedAddressWasDefault() {
        stubLock();
        Address defaultAddress = existingAddress(100L, true);
        Address remaining = existingAddress(101L, false);
        when(addressRepository.findByIdAndCustomerId(100L, 1L)).thenReturn(Optional.of(defaultAddress));
        when(addressRepository.findFirstByCustomerIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.of(remaining));

        addressService.deleteAddress(100L);

        verify(addressRepository).delete(defaultAddress);
        assertThat(remaining.isDefault()).isTrue();
        verify(addressRepository).save(remaining);
    }

    @Test
    void deleteAddress_leavesNoDefault_whenNoAddressesRemain() {
        stubLock();
        Address defaultAddress = existingAddress(100L, true);
        when(addressRepository.findByIdAndCustomerId(100L, 1L)).thenReturn(Optional.of(defaultAddress));
        when(addressRepository.findFirstByCustomerIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.empty());

        addressService.deleteAddress(100L);

        verify(addressRepository, never()).save(any());
    }

    @Test
    void mutatingMethods_throwUnauthenticated_whenCallerHasNoUserRow() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.createAddress(request("Home", "Street 1", "Cairo", false)))
                .isInstanceOf(UnauthenticatedException.class);
    }

    private void stubLock() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(new User()));
    }

    private AddressRequest request(String label, String street, String city, boolean isDefault) {
        AddressRequest request = new AddressRequest();
        request.setLabel(label);
        request.setStreet(street);
        request.setCity(city);
        request.setDefault(isDefault);
        return request;
    }

    private Address existingAddress(Long id, boolean isDefault) {
        Address address = new Address();
        address.setId(id);
        address.setLabel("Home");
        address.setStreet("Street 1");
        address.setCity("Cairo");
        address.setDefault(isDefault);
        return address;
    }

    private org.mockito.stubbing.Answer<Address> answerWithGeneratedId(Long id) {
        return invocation -> {
            Address address = invocation.getArgument(0);
            address.setId(id);
            return address;
        };
    }
}
