package com.food.foodapp.cart.repository;

import com.food.foodapp.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    void deleteByCartId(Long cartId);
}
