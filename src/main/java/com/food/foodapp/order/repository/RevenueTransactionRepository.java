package com.food.foodapp.order.repository;

import com.food.foodapp.order.entity.RevenueTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevenueTransactionRepository extends JpaRepository<RevenueTransaction, Long> {

    /**
     * Whether an order's revenue has already been recorded — a pre-check
     * {@code OrderService#deliverOrder} can use before attempting the insert, on top of the
     * database's own {@code UNIQUE(order_id)} constraint (see {@link RevenueTransaction}), which is
     * what actually guarantees no duplicate row can ever be committed.
     */
    boolean existsByOrderId(Long orderId);
}
