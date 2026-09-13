package com.food.foodapp.order.repository;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.entity.RevenueTransaction;
import com.food.foodapp.order.entity.RevenueTransactionType;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.support.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence-level checks for the revenue ledger's {@code UNIQUE(order_id)} constraint (see
 * {@link RevenueTransaction}) — the hard, database-enforced backstop behind
 * {@code OrderService#deliverOrder}'s idempotency guarantee that a second attempt to record an
 * order's revenue can never succeed, even if every application-level guard (the {@link OrderStatus}
 * transition table, the pessimistic row lock) were somehow bypassed.
 */
@RepositoryTest
class RevenueTransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RevenueTransactionRepository revenueTransactionRepository;

    @Test
    void savingASecondTransaction_forTheSameOrder_violatesUniqueConstraint() {
        User customer = persistUser("revenue-owner-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order order = persistOrder(customer, restaurant);

        revenueTransactionRepository.saveAndFlush(revenueTransaction(order, restaurant, BigDecimal.valueOf(112)));

        assertThatThrownBy(() -> revenueTransactionRepository.saveAndFlush(
                revenueTransaction(order, restaurant, BigDecimal.valueOf(112))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void existsByOrderId_reflectsWhetherRevenueHasAlreadyBeenRecorded() {
        User customer = persistUser("revenue-exists-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order order = persistOrder(customer, restaurant);

        assertThat(revenueTransactionRepository.existsByOrderId(order.getId())).isFalse();

        revenueTransactionRepository.saveAndFlush(revenueTransaction(order, restaurant, BigDecimal.valueOf(112)));

        assertThat(revenueTransactionRepository.existsByOrderId(order.getId())).isTrue();
    }

    @Test
    void savedTransaction_defaultsTypeToOrderPayment_andStampsCreatedAt() {
        User customer = persistUser("revenue-defaults-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order order = persistOrder(customer, restaurant);

        RevenueTransaction saved = revenueTransactionRepository.saveAndFlush(
                revenueTransaction(order, restaurant, BigDecimal.valueOf(112)));

        assertThat(saved.getType()).isEqualTo(RevenueTransactionType.ORDER_PAYMENT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(112));
    }

    private RevenueTransaction revenueTransaction(Order order, Restaurant restaurant, BigDecimal amount) {
        RevenueTransaction transaction = new RevenueTransaction();
        transaction.setOrder(order);
        transaction.setRestaurant(restaurant);
        transaction.setAmount(amount);
        transaction.setType(RevenueTransactionType.ORDER_PAYMENT);
        return transaction;
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
        entityManager.persist(restaurant);
        return restaurant;
    }

    private Order persistOrder(User customer, Restaurant restaurant) {
        Order order = new Order();
        order.setOrderNumber("ORD-TEST-" + System.nanoTime());
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setDeliveryStreet("Street 1");
        order.setDeliveryCity("Cairo");
        order.setSubtotal(BigDecimal.valueOf(100));
        order.setDeliveryFee(BigDecimal.valueOf(12));
        order.setTotal(BigDecimal.valueOf(112));
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        entityManager.persist(order);
        entityManager.flush();
        return order;
    }
}
