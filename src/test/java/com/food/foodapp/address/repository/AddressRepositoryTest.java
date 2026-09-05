package com.food.foodapp.address.repository;

import com.food.foodapp.address.entity.Address;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.food.foodapp.support.RepositoryTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class AddressRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AddressRepository addressRepository;

    @Test
    void findByCustomerId_returnsDefaultAddressFirst() {
        User customer = persistUser("owner-" + System.nanoTime() + "@example.com");
        Address nonDefault = persistAddress(customer, "Work", false);
        Address defaultAddress = persistAddress(customer, "Home", true);

        List<Address> found = addressRepository.findByCustomerId(customer.getId());

        assertThat(found).extracting(Address::getId).containsExactly(defaultAddress.getId(), nonDefault.getId());
    }

    @Test
    void findByIdAndCustomerId_isEmpty_forAnotherCustomersAddress() {
        User owner = persistUser("owner-" + System.nanoTime() + "@example.com");
        User stranger = persistUser("stranger-" + System.nanoTime() + "@example.com");
        Address address = persistAddress(owner, "Home", false);

        Optional<Address> found = addressRepository.findByIdAndCustomerId(address.getId(), stranger.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndCustomerId_returnsAddress_forItsOwner() {
        User owner = persistUser("owner-" + System.nanoTime() + "@example.com");
        Address address = persistAddress(owner, "Home", false);

        Optional<Address> found = addressRepository.findByIdAndCustomerId(address.getId(), owner.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLabel()).isEqualTo("Home");
    }

    @Test
    void findFirstByCustomerIdOrderByCreatedAtAsc_returnsOldestAddress() {
        User customer = persistUser("owner-" + System.nanoTime() + "@example.com");
        Address oldest = persistAddress(customer, "Oldest", false);
        persistAddress(customer, "Newest", false);

        Optional<Address> found = addressRepository.findFirstByCustomerIdOrderByCreatedAtAsc(customer.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(oldest.getId());
    }

    @Test
    void clearDefaultForOthers_unsetsOnlyOtherAddresses_forThatCustomer() {
        User customer = persistUser("owner-" + System.nanoTime() + "@example.com");
        User otherCustomer = persistUser("other-" + System.nanoTime() + "@example.com");
        Address first = persistAddress(customer, "First", true);
        Address second = persistAddress(customer, "Second", true);
        Address unrelated = persistAddress(otherCustomer, "Unrelated", true);
        entityManager.flush();

        addressRepository.clearDefaultForOthers(customer.getId(), second.getId());
        entityManager.clear();

        assertThat(addressRepository.findById(first.getId()).orElseThrow().isDefault()).isFalse();
        assertThat(addressRepository.findById(second.getId()).orElseThrow().isDefault()).isTrue();
        assertThat(addressRepository.findById(unrelated.getId()).orElseThrow().isDefault()).isTrue();
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Address Owner");
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(Role.CUSTOMER);
        entityManager.persist(user);
        return user;
    }

    private Address persistAddress(User customer, String label, boolean isDefault) {
        Address address = new Address();
        address.setCustomer(customer);
        address.setLabel(label);
        address.setStreet("Street " + label);
        address.setCity("Cairo");
        address.setDefault(isDefault);
        entityManager.persistAndFlush(address);
        return address;
    }
}
