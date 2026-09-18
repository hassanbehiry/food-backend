package com.food.foodapp.cart.service;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.dto.CartAddItemRequest;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.repository.CartRepository;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.menu.repository.MenuCategoryRepository;
import com.food.foodapp.menu.repository.MenuItemRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Proves the pessimistic lock in {@link CartService#addItem} does what
 * {@code CartRepository#findByCustomerIdForUpdate}'s javadoc claims: two genuinely
 * concurrent "add this menu item" calls for the same cart merge into one row instead
 * of racing each other into two, or into a unique-constraint violation. Runs against
 * the real Postgres instance (via {@code spring-boot-docker-compose}), not mocks,
 * because the property under test only exists at the transaction/lock level. Runs outside a
 * test-managed transaction for the same reason (the racing threads need to see each other's
 * committed rows, which a single enclosing transaction would hide), so every row it persists
 * is torn down explicitly in {@link #tearDown()} instead of relying on rollback.
 */
@SpringBootTest
class CartServiceConcurrencyTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuCategoryRepository menuCategoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @MockitoBean
    private UserContext userContext;

    private User customer;
    private Restaurant restaurant;
    private MenuCategory category;
    private MenuItem menuItem;

    @AfterEach
    void tearDown() {
        // No enclosing test transaction to roll back (see class javadoc), so every row this test
        // committed to the real database is deleted explicitly, in FK-safe order: the menu item's
        // ON DELETE CASCADE takes the racing threads' cart_items rows with it, then the cart itself
        // (looked up fresh since the threads may have created it, not just the pre-seeded one from
        // cartService.getCart()) can be removed, followed by its now-unreferenced category,
        // restaurant and customer.
        if (menuItem != null) {
            menuItemRepository.deleteById(menuItem.getId());
        }
        if (customer != null) {
            cartRepository.findByCustomerIdWithItems(customer.getId())
                    .ifPresent(cart -> cartRepository.deleteById(cart.getId()));
        }
        if (category != null) {
            menuCategoryRepository.deleteById(category.getId());
        }
        if (restaurant != null) {
            restaurantRepository.deleteById(restaurant.getId());
        }
        if (customer != null) {
            userRepository.deleteById(customer.getId());
        }
    }

    @Test
    void addItem_concurrentRequestsForSameMenuItem_mergeIntoOneRow_insteadOfDuplicating() throws Exception {
        customer = persistUser("concurrent-cart-" + System.nanoTime() + "@example.com");
        when(userContext.getCurrentUserId()).thenReturn(customer.getId());

        restaurant = persistRestaurant("Concurrency Test Restaurant");
        category = persistCategory(restaurant);
        menuItem = persistMenuItem(restaurant, category);

        // Ensure the cart row already exists so both threads race on the same lock target
        // rather than on the separate, out-of-scope "first ever cart" creation race.
        cartService.getCart();

        CartAddItemRequest request = new CartAddItemRequest();
        request.setMenuItemId(menuItem.getId());
        request.setQuantity(1);

        int concurrentRequests = 5;
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CyclicBarrier barrier = new CyclicBarrier(concurrentRequests);
        Callable<Void> addOne = () -> {
            barrier.await();
            cartService.addItem(request);
            return null;
        };

        try {
            List<Future<Void>> futures = pool.invokeAll(List.of(addOne, addOne, addOne, addOne, addOne));
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        Cart cart = cartRepository.findByCustomerIdWithItems(customer.getId()).orElseThrow();
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(concurrentRequests);
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Concurrency Test Customer");
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(Role.CUSTOMER);
        return userRepository.save(user);
    }

    private Restaurant persistRestaurant(String name) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setCuisine(name);
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        return restaurantRepository.save(restaurant);
    }

    private MenuCategory persistCategory(Restaurant restaurant) {
        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName("Mains");
        category.setDisplayOrder(0);
        category.setActive(true);
        return menuCategoryRepository.save(category);
    }

    private MenuItem persistMenuItem(Restaurant restaurant, MenuCategory category) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName("Concurrency Test Item");
        item.setPrice(BigDecimal.valueOf(20));
        item.setDisplayOrder(0);
        item.setAvailable(true);
        return menuItemRepository.save(item);
    }
}
