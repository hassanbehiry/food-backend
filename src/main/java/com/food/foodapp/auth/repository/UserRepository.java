package com.food.foodapp.auth.repository;

import com.food.foodapp.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Row-locks the user for the duration of the caller's transaction. {@code AddressService}
     * takes this lock before any mutation that can change which address is the customer's
     * default: unlike a cart (one row per customer, so the row itself can be locked),
     * a customer can have zero addresses, so there is nothing address-side to lock against
     * the race of two concurrent "first address" inserts both trying to become the default.
     * The user row always exists once authenticated, so locking it instead gives every
     * default-changing operation for a given customer a single point of serialization.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
