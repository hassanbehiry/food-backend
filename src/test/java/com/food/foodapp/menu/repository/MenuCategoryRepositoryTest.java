package com.food.foodapp.menu.repository;

import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MenuCategoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MenuCategoryRepository menuCategoryRepository;

    @Test
    void findByRestaurantIdAndActiveTrue_excludesInactiveAndOrdersByDisplayOrder() {
        Restaurant restaurant = persistRestaurant("Pizza Place");
        entityManager.persist(menuCategory(restaurant, "مشروبات", 1, true));
        entityManager.persist(menuCategory(restaurant, "بيتزا", 0, true));
        entityManager.persist(menuCategory(restaurant, "قديم", 2, false));
        entityManager.flush();
        entityManager.clear();

        List<MenuCategory> result = menuCategoryRepository
                .findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(restaurant.getId());

        assertThat(result).extracting(MenuCategory::getName).containsExactly("بيتزا", "مشروبات");
    }

    @Test
    void findByIdAndRestaurantId_isEmpty_whenCategoryBelongsToAnotherRestaurant() {
        Restaurant restaurantA = persistRestaurant("Restaurant A");
        Restaurant restaurantB = persistRestaurant("Restaurant B");
        MenuCategory category = menuCategory(restaurantA, "بيتزا", 0, true);
        entityManager.persist(category);
        entityManager.flush();
        entityManager.clear();

        Optional<MenuCategory> found = menuCategoryRepository.findByIdAndRestaurantId(category.getId(), restaurantB.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void existsByRestaurantIdAndNameIgnoreCase_isCaseInsensitiveAndRestaurantScoped() {
        Restaurant restaurantA = persistRestaurant("Restaurant A");
        Restaurant restaurantB = persistRestaurant("Restaurant B");
        entityManager.persist(menuCategory(restaurantA, "Pizza", 0, true));
        entityManager.flush();
        entityManager.clear();

        assertThat(menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantA.getId(), "pizza")).isTrue();
        assertThat(menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantB.getId(), "pizza")).isFalse();
    }

    @Test
    void findMaxDisplayOrder_returnsMinusOne_whenRestaurantHasNoCategories() {
        Restaurant restaurant = persistRestaurant("Empty Menu Place");
        entityManager.flush();

        assertThat(menuCategoryRepository.findMaxDisplayOrder(restaurant.getId())).isEqualTo(-1);
    }

    private Restaurant persistRestaurant(String name) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setCuisine(name);
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(30));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurant.setOpenForOrders(true);
        entityManager.persist(restaurant);
        return restaurant;
    }

    private MenuCategory menuCategory(Restaurant restaurant, String name, int displayOrder, boolean active) {
        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setActive(active);
        return category;
    }
}
