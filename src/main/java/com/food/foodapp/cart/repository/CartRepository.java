package com.food.foodapp.cart.repository;

import com.food.foodapp.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("SELECT c FROM Cart c "
            + "LEFT JOIN FETCH c.restaurant "
            + "LEFT JOIN FETCH c.items i "
            + "LEFT JOIN FETCH i.menuItem "
            + "WHERE c.customer.id = :customerId")
    Optional<Cart> findByCustomerIdWithItems(@Param("customerId") Long customerId);

    /**
     * Row-locks the customer's cart for the duration of the caller's transaction, without
     * joining any collections (Postgres rejects {@code FOR UPDATE} across an outer join). Every
     * mutation acquires this lock first so two concurrent requests against the same cart (e.g. a
     * double-tap "add to cart", or overlapping sync calls) serialize instead of racing a
     * check-then-act read against the item collection, which is what would otherwise let the
     * same {@code menuItemId} end up inserted twice before either request commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.customer.id = :customerId")
    Optional<Cart> findByCustomerIdForUpdate(@Param("customerId") Long customerId);
}
