package com.food.foodapp.menu.repository;

import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.food.foodapp.support.RepositoryTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class MenuItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Test
    void findVisibleByRestaurantId_excludesItemsInInactiveCategories_andOrdersByCategoryThenItem() {
        Restaurant restaurant = persistRestaurant("Pizza Place");
        MenuCategory pizza = persistCategory(restaurant, "بيتزا", 0, true);
        MenuCategory drinks = persistCategory(restaurant, "مشروبات", 1, true);
        MenuCategory archived = persistCategory(restaurant, "قديم", 2, false);

        entityManager.persist(menuItem(restaurant, drinks, "كولا", 0));
        entityManager.persist(menuItem(restaurant, pizza, "مارجريتا", 0));
        entityManager.persist(menuItem(restaurant, archived, "منتج قديم", 0));
        entityManager.flush();
        entityManager.clear();

        List<MenuItem> result = menuItemRepository.findVisibleByRestaurantId(restaurant.getId());

        assertThat(result).extracting(MenuItem::getName).containsExactly("مارجريتا", "كولا");
    }

    @Test
    void findByIdAndRestaurantId_isEmpty_whenItemBelongsToAnotherRestaurant() {
        Restaurant restaurantA = persistRestaurant("Restaurant A");
        Restaurant restaurantB = persistRestaurant("Restaurant B");
        MenuCategory category = persistCategory(restaurantA, "بيتزا", 0, true);
        MenuItem item = menuItem(restaurantA, category, "مارجريتا", 0);
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        Optional<MenuItem> found = menuItemRepository.findByIdAndRestaurantId(item.getId(), restaurantB.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findMaxDisplayOrderInCategory_returnsMinusOne_whenCategoryHasNoItems() {
        Restaurant restaurant = persistRestaurant("Empty Menu Place");
        MenuCategory category = persistCategory(restaurant, "بيتزا", 0, true);
        entityManager.flush();

        assertThat(menuItemRepository.findMaxDisplayOrderInCategory(category.getId())).isEqualTo(-1);
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

    private MenuCategory persistCategory(Restaurant restaurant, String name, int displayOrder, boolean active) {
        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setActive(active);
        entityManager.persist(category);
        return category;
    }

    private MenuItem menuItem(Restaurant restaurant, MenuCategory category, String name, int displayOrder) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName(name);
        item.setPrice(BigDecimal.valueOf(25));
        item.setDisplayOrder(displayOrder);
        return item;
    }
}
