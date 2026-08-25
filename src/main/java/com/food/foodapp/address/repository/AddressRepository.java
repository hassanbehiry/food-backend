package com.food.foodapp.address.repository;

import com.food.foodapp.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    @Query("SELECT a FROM Address a WHERE a.customer.id = :customerId ORDER BY a.isDefault DESC, a.createdAt ASC")
    List<Address> findByCustomerId(@Param("customerId") Long customerId);

    Optional<Address> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Address> findFirstByCustomerIdOrderByCreatedAtAsc(Long customerId);

    /** Used by every default-setting path to clear the previous default before setting a new one. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.customer.id = :customerId AND a.id <> :keepId AND a.isDefault = true")
    void clearDefaultForOthers(@Param("customerId") Long customerId, @Param("keepId") Long keepId);
}
