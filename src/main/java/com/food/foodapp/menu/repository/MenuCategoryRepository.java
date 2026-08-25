package com.food.foodapp.menu.repository;

import com.food.foodapp.menu.entity.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long restaurantId);

    List<MenuCategory> findByRestaurantIdOrderByDisplayOrderAscIdAsc(Long restaurantId);

    Optional<MenuCategory> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);

    @Query("SELECT COALESCE(MAX(m.displayOrder), -1) FROM MenuCategory m WHERE m.restaurant.id = :restaurantId")
    int findMaxDisplayOrder(@Param("restaurantId") Long restaurantId);
}
