package com.food.foodapp.cart.repository;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.menu.repository.MenuItemRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Test
    void findByCustomerIdWithItems_loadsItemsAndMenuItemsWithoutLazyInitializationException() {
        User customer = persistUser("cart-owner-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        MenuCategory category = persistCategory(restaurant);
        MenuItem menuItem = persistMenuItem(restaurant, category, "مارجريتا", BigDecimal.valueOf(50));

        Cart cart = new Cart();
        cart.setCustomer(customer);
        cart.setRestaurant(restaurant);
        entityManager.persist(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setMenuItem(menuItem);
        item.setQuantity(3);
        entityManager.persist(item);

        entityManager.flush();
        entityManager.clear();

        Optional<Cart> found = cartRepository.findByCustomerIdWithItems(customer.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).getMenuItem().getName()).isEqualTo("مارجريتا");
        assertThat(found.get().getRestaurant().getName()).isEqualTo("Pizza Place");
    }

    @Test
    void findByCustomerIdWithItems_isEmpty_whenCustomerHasNoCart() {
        User customer = persistUser("no-cart-" + System.nanoTime() + "@example.com");
        entityManager.flush();

        Optional<Cart> found = cartRepository.findByCustomerIdWithItems(customer.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findByCustomerIdForUpdate_locksAndReturnsCart_withoutJoiningItems() {
        User customer = persistUser("lock-owner-" + System.nanoTime() + "@example.com");
        Cart cart = new Cart();
        cart.setCustomer(customer);
        entityManager.persist(cart);
        entityManager.flush();
        entityManager.clear();

        Optional<Cart> found = cartRepository.findByCustomerIdForUpdate(customer.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(cart.getId());
    }

    @Test
    void deletingMenuItem_cascadesToItsCartItems_insteadOfViolatingForeignKey() {
        User customer = persistUser("cascade-owner-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Burger Place");
        MenuCategory category = persistCategory(restaurant);
        MenuItem menuItem = persistMenuItem(restaurant, category, "برجر", BigDecimal.valueOf(40));

        Cart cart = new Cart();
        cart.setCustomer(customer);
        cart.setRestaurant(restaurant);
        entityManager.persist(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setMenuItem(menuItem);
        item.setQuantity(2);
        entityManager.persist(item);
        entityManager.flush();
        Long cartItemId = item.getId();

        menuItemRepository.delete(menuItem);
        entityManager.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findById(cartItemId)).isEmpty();
    }

    @Test
    void savingSecondCartItem_forSameMenuItem_violatesUniqueConstraint() {
        User customer = persistUser("dup-owner-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Sushi Place");
        MenuCategory category = persistCategory(restaurant);
        MenuItem menuItem = persistMenuItem(restaurant, category, "سوشي", BigDecimal.valueOf(60));

        Cart cart = new Cart();
        cart.setCustomer(customer);
        cart.setRestaurant(restaurant);
        entityManager.persist(cart);

        CartItem first = new CartItem();
        first.setCart(cart);
        first.setMenuItem(menuItem);
        first.setQuantity(1);
        entityManager.persist(first);
        entityManager.flush();

        CartItem duplicate = new CartItem();
        duplicate.setCart(cart);
        duplicate.setMenuItem(menuItem);
        duplicate.setQuantity(1);

        assertThatThrownBy(() -> {
            cartItemRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Cart Owner");
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(Role.CUSTOMER);
        entityManager.persist(user);
        return user;
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

    private MenuCategory persistCategory(Restaurant restaurant) {
        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName("بيتزا");
        category.setDisplayOrder(0);
        category.setActive(true);
        entityManager.persist(category);
        return category;
    }

    private MenuItem persistMenuItem(Restaurant restaurant, MenuCategory category, String name, BigDecimal price) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName(name);
        item.setPrice(price);
        item.setDisplayOrder(0);
        item.setAvailable(true);
        entityManager.persist(item);
        return item;
    }
}
