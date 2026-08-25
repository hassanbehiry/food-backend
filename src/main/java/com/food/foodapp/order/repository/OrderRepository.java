package com.food.foodapp.order.repository;

import com.food.foodapp.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Scoped to {@code customerId} so an id belonging to another customer is indistinguishable
     * from one that doesn't exist at all — same ownership pattern {@code AddressRepository} uses.
     */
    @Query("SELECT o FROM Order o "
            + "LEFT JOIN FETCH o.items "
            + "LEFT JOIN FETCH o.restaurant "
            + "WHERE o.id = :id AND o.customer.id = :customerId")
    Optional<Order> findByIdAndCustomerIdWithItems(@Param("id") Long id, @Param("customerId") Long customerId);
}
