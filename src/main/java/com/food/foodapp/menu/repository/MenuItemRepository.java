package com.food.foodapp.menu.repository;

import com.food.foodapp.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** Fetch-joins the restaurant so a caller validating several items at once (e.g. cart sync) avoids N+1. */
    @Query("SELECT i FROM MenuItem i JOIN FETCH i.restaurant WHERE i.id IN :ids")
    List<MenuItem> findAllByIdWithRestaurant(@Param("ids") Collection<Long> ids);

    List<MenuItem> findByCategoryIdOrderByDisplayOrderAscIdAsc(Long categoryId);

    /** Fetch-joins the category so mapping to the customer-facing {@code tab} name avoids N+1. */
    @Query("SELECT i FROM MenuItem i JOIN FETCH i.category "
            + "WHERE i.restaurant.id = :restaurantId AND i.category.active = true "
            + "ORDER BY i.category.displayOrder ASC, i.displayOrder ASC, i.id ASC")
    List<MenuItem> findVisibleByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * All items regardless of category visibility — owner management view. Fetch-joins the
     * category so mapping each item's {@code tab} name avoids N+1.
     */
    @Query("SELECT i FROM MenuItem i JOIN FETCH i.category WHERE i.restaurant.id = :restaurantId "
            + "ORDER BY i.category.displayOrder ASC, i.displayOrder ASC, i.id ASC")
    List<MenuItem> findAllByRestaurantIdOrdered(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COALESCE(MAX(i.displayOrder), -1) FROM MenuItem i WHERE i.category.id = :categoryId")
    int findMaxDisplayOrderInCategory(@Param("categoryId") Long categoryId);
}
