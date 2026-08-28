package com.food.foodapp.order.repository;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.menu.repository.MenuItemRepository;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderItem;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.hibernate.exception.ConstraintViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence-level checks for the order-item snapshot: the constraints that back the
 * "old orders remain historically accurate" and "persistence constraints protect required
 * relationships" acceptance criteria, which a mapper/service unit test can't exercise because
 * they only bite at flush time against the real schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Test
    void orderItemSnapshot_staysUnchanged_afterMenuItemIsEditedThenDeleted() {
        User customer = persistUser("order-owner-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        MenuCategory category = persistCategory(restaurant);
        MenuItem menuItem = persistMenuItem(restaurant, category, "Margherita", BigDecimal.valueOf(50));

        Order order = persistOrder(customer, restaurant);
        OrderItem item = new OrderItem(order, menuItem.getId(), menuItem.getName(), menuItem.getImageUrl(),
                menuItem.getPrice(), 2, menuItem.getPrice().multiply(BigDecimal.valueOf(2)));
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        menuItem = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        menuItem.setName("Margherita Deluxe");
        menuItem.setPrice(BigDecimal.valueOf(90));
        menuItemRepository.saveAndFlush(menuItem);
        menuItemRepository.delete(menuItem);
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findByIdAndCustomerIdWithItems(order.getId(), customer.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(1);
        OrderItem reloadedItem = reloaded.getItems().get(0);
        assertThat(reloadedItem.getName()).isEqualTo("Margherita");
        assertThat(reloadedItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(reloadedItem.getLineTotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void savingOrderItem_withoutMenuItemId_violatesNotNullConstraint() {
        User customer = persistUser("no-menu-item-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Burger Place");
        Order order = persistOrder(customer, restaurant);

        OrderItem item = new OrderItem(order, null, "Burger", null, BigDecimal.valueOf(40), 1, BigDecimal.valueOf(40));

        assertThatThrownBy(() -> {
            entityManager.persist(item);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void savingOrderItem_withoutOrder_violatesNotNullConstraint() {
        OrderItem item = new OrderItem(null, 10L, "Burger", null, BigDecimal.valueOf(40), 1, BigDecimal.valueOf(40));

        assertThatThrownBy(() -> {
            entityManager.persist(item);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Order Owner");
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
        category.setName("Pizza");
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

    private Order persistOrder(User customer, Restaurant restaurant) {
        Order order = new Order();
        order.setOrderNumber("ORD-TEST-" + System.nanoTime());
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setDeliveryStreet("Street 1");
        order.setDeliveryCity("Cairo");
        order.setSubtotal(BigDecimal.valueOf(100));
        order.setDeliveryFee(BigDecimal.valueOf(10));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(110));
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setStatus(OrderStatus.PENDING);
        entityManager.persist(order);
        entityManager.flush();
        return order;
    }
}
