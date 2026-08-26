package com.food.foodapp.favorite.repository;

import com.food.foodapp.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Query("SELECT f FROM Favorite f JOIN FETCH f.restaurant WHERE f.customer.id = :customerId ORDER BY f.createdAt DESC")
    List<Favorite> findByCustomerIdWithRestaurant(@Param("customerId") Long customerId);

    Optional<Favorite> findByCustomerIdAndRestaurantId(Long customerId, Long restaurantId);
}
