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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Persistence-level checks for the order-item snapshot: the constraints that back the
 * "old orders remain historically accurate" and "persistence constraints protect required
 * relationships" acceptance criteria, which a mapper/service unit test can't exercise because
 * they only bite at flush time against the real schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    /** A wide-open bound pair standing in for "no date filter" — {@code findByCustomerIdWithFilters} never accepts {@code null} here (see its javadoc). */
    private static final LocalDateTime ANY_FROM_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime ANY_TO_DATE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

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

    @Test
    void findByIdAndRestaurantIdWithItems_isEmpty_whenOrderBelongsToADifferentRestaurant() {
        User customer = persistUser("owner-scoping-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Restaurant otherRestaurant = persistRestaurant("Burger Place");
        Order order = persistOrder(customer, restaurant);

        assertThat(orderRepository.findByIdAndRestaurantIdWithItems(order.getId(), otherRestaurant.getId()))
                .isEmpty();
        assertThat(orderRepository.findByIdAndRestaurantIdWithItems(order.getId(), restaurant.getId()))
                .isPresent();
    }

    @Test
    void findByIdAndRestaurantIdWithItems_alsoFetchesCustomer_forTheOwnerDetailView() {
        User customer = persistUser("owner-detail-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order order = persistOrder(customer, restaurant);
        entityManager.clear();

        Order reloaded = orderRepository.findByIdAndRestaurantIdWithItems(order.getId(), restaurant.getId())
                .orElseThrow();

        assertThat(reloaded.getCustomer().getName()).isEqualTo("Order Owner");
    }

    @Test
    void findByRestaurantIdAndOptionalStatus_filtersByStatus_whenGiven() {
        User customer = persistUser("owner-list-status-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        persistOrder(customer, restaurant);
        Order preparingOrder = persistOrder(customer, restaurant);
        preparingOrder.setStatus(OrderStatus.PREPARING);
        entityManager.flush();
        entityManager.clear();

        Page<Order> preparingOnly = orderRepository.findByRestaurantIdAndOptionalStatus(
                restaurant.getId(), OrderStatus.PREPARING, PageRequest.of(0, 20));

        assertThat(preparingOnly.getContent()).extracting(Order::getId).containsExactly(preparingOrder.getId());
    }

    @Test
    void findByRestaurantIdAndOptionalStatus_returnsEveryStatus_whenStatusIsNull() {
        User customer = persistUser("owner-list-all-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        persistOrder(customer, restaurant);
        Order cancelledOrder = persistOrder(customer, restaurant);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        entityManager.flush();
        entityManager.clear();

        Page<Order> all = orderRepository.findByRestaurantIdAndOptionalStatus(
                restaurant.getId(), null, PageRequest.of(0, 20));

        assertThat(all.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByRestaurantIdAndOptionalStatus_isScopedToRestaurant() {
        User customer = persistUser("owner-list-scope-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Restaurant otherRestaurant = persistRestaurant("Burger Place");
        persistOrder(customer, restaurant);
        persistOrder(customer, otherRestaurant);

        Page<Order> result = orderRepository.findByRestaurantIdAndOptionalStatus(
                restaurant.getId(), null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void countByRestaurantIdAndStatus_andCountByRestaurantId_areScopedToRestaurant() {
        User customer = persistUser("owner-count-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Restaurant otherRestaurant = persistRestaurant("Burger Place");
        persistOrder(customer, restaurant);
        Order preparingOrder = persistOrder(customer, restaurant);
        preparingOrder.setStatus(OrderStatus.PREPARING);
        persistOrder(customer, otherRestaurant);
        entityManager.flush();

        assertThat(orderRepository.countByRestaurantId(restaurant.getId())).isEqualTo(2);
        assertThat(orderRepository.countByRestaurantIdAndStatus(restaurant.getId(), OrderStatus.NEW)).isEqualTo(1);
        assertThat(orderRepository.countByRestaurantIdAndStatus(restaurant.getId(), OrderStatus.PREPARING))
                .isEqualTo(1);
    }

    @Test
    void findByCustomerIdWithFilters_isScopedToCustomer_andOrdersNewestFirst() {
        User customer = persistUser("history-scope-" + System.nanoTime() + "@example.com");
        User otherCustomer = persistUser("history-scope-other-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order older = persistOrder(customer, restaurant);
        Order newer = persistOrder(customer, restaurant);
        persistOrder(otherCustomer, restaurant);
        entityManager.clear();

        Page<Order> result = orderRepository.findByCustomerIdWithFilters(
                customer.getId(), null, null, ANY_FROM_DATE, ANY_TO_DATE, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Order::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void findByCustomerIdWithFilters_filtersByStatus_whenGiven() {
        User customer = persistUser("history-status-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        persistOrder(customer, restaurant);
        Order cancelled = persistOrder(customer, restaurant);
        cancelled.setStatus(OrderStatus.CANCELLED);
        entityManager.flush();
        entityManager.clear();

        Page<Order> result = orderRepository.findByCustomerIdWithFilters(
                customer.getId(), OrderStatus.CANCELLED, null, ANY_FROM_DATE, ANY_TO_DATE, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Order::getId).containsExactly(cancelled.getId());
    }

    @Test
    void findByCustomerIdWithFilters_filtersByRestaurant_whenGiven() {
        User customer = persistUser("history-restaurant-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Restaurant otherRestaurant = persistRestaurant("Burger Place");
        Order atRestaurant = persistOrder(customer, restaurant);
        persistOrder(customer, otherRestaurant);
        entityManager.clear();

        Page<Order> result = orderRepository.findByCustomerIdWithFilters(
                customer.getId(), null, restaurant.getId(), ANY_FROM_DATE, ANY_TO_DATE, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Order::getId).containsExactly(atRestaurant.getId());
    }

    @Test
    void findByCustomerIdWithFilters_filtersByDateRange_asAnInclusiveWholeDaySpan() {
        User customer = persistUser("history-date-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order inRange = persistOrder(customer, restaurant);
        setCreatedAt(inRange, LocalDateTime.of(2026, 8, 10, 12, 0));
        Order beforeRange = persistOrder(customer, restaurant);
        setCreatedAt(beforeRange, LocalDateTime.of(2026, 8, 9, 23, 59));
        Order afterRange = persistOrder(customer, restaurant);
        setCreatedAt(afterRange, LocalDateTime.of(2026, 8, 11, 0, 0));
        entityManager.clear();

        Page<Order> result = orderRepository.findByCustomerIdWithFilters(customer.getId(), null, null,
                LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 11, 0, 0), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Order::getId).containsExactly(inRange.getId());
    }

    @Test
    void sumItemQuantitiesByOrderIds_sumsQuantityAcrossLines_andOmitsOrdersWithNoMatchingLines() {
        User customer = persistUser("history-items-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        MenuCategory category = persistCategory(restaurant);
        MenuItem menuItem = persistMenuItem(restaurant, category, "Pizza", BigDecimal.valueOf(50));
        Order order = persistOrder(customer, restaurant);
        entityManager.persist(new OrderItem(order, menuItem.getId(), menuItem.getName(), null,
                menuItem.getPrice(), 2, menuItem.getPrice().multiply(BigDecimal.valueOf(2))));
        entityManager.persist(new OrderItem(order, menuItem.getId(), menuItem.getName(), null,
                menuItem.getPrice(), 3, menuItem.getPrice().multiply(BigDecimal.valueOf(3))));
        Order orderWithNoItems = persistOrder(customer, restaurant);
        entityManager.flush();
        entityManager.clear();

        List<OrderItemCount> counts = orderRepository.sumItemQuantitiesByOrderIds(
                List.of(order.getId(), orderWithNoItems.getId()));

        assertThat(counts).extracting(OrderItemCount::orderId, OrderItemCount::itemCount)
                .containsExactly(tuple(order.getId(), 5L));
    }

    @Test
    void countByCreatedAtGreaterThanEqualAndCreatedAtLessThan_countsEveryStatusAcrossRestaurants_inRangeOnly() {
        User customer = persistUser("admin-count-" + System.nanoTime() + "@example.com");
        Restaurant pizzaPlace = persistRestaurant("Pizza Place");
        Restaurant burgerPlace = persistRestaurant("Burger Place");
        Order inRangeCancelled = persistOrder(customer, pizzaPlace);
        inRangeCancelled.setStatus(OrderStatus.CANCELLED);
        Order inRangeAtOtherRestaurant = persistOrder(customer, burgerPlace);
        setCreatedAt(inRangeCancelled, LocalDateTime.of(2026, 8, 10, 12, 0));
        setCreatedAt(inRangeAtOtherRestaurant, LocalDateTime.of(2026, 8, 15, 8, 0));
        Order outOfRange = persistOrder(customer, pizzaPlace);
        setCreatedAt(outOfRange, LocalDateTime.of(2026, 9, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        long count = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void sumRevenueByStatusInRange_sumsAcrossRestaurants_onlyTheGivenStatusInRange() {
        User customer = persistUser("admin-revenue-" + System.nanoTime() + "@example.com");
        Restaurant pizzaPlace = persistRestaurant("Pizza Place");
        Restaurant burgerPlace = persistRestaurant("Burger Place");
        Order delivered1 = persistOrder(customer, pizzaPlace);
        delivered1.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered1, LocalDateTime.of(2026, 8, 10, 12, 0));
        Order delivered2 = persistOrder(customer, burgerPlace);
        delivered2.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered2, LocalDateTime.of(2026, 8, 20, 12, 0));
        Order cancelled = persistOrder(customer, pizzaPlace);
        cancelled.setStatus(OrderStatus.CANCELLED);
        setCreatedAt(cancelled, LocalDateTime.of(2026, 8, 12, 12, 0));
        entityManager.flush();
        entityManager.clear();

        RevenueAggregate result = orderRepository.sumRevenueByStatusInRange(
                OrderStatus.DELIVERED, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result.orderCount()).isEqualTo(2);
        assertThat(result.totalRevenueOrZero()).isEqualByComparingTo(BigDecimal.valueOf(220));
    }

    @Test
    void countGroupByStatusInRange_groupsAcrossRestaurants_omittingStatusesWithNoOrders() {
        User customer = persistUser("admin-status-" + System.nanoTime() + "@example.com");
        Restaurant pizzaPlace = persistRestaurant("Pizza Place");
        Restaurant burgerPlace = persistRestaurant("Burger Place");
        Order newOrder = persistOrder(customer, pizzaPlace);
        setCreatedAt(newOrder, LocalDateTime.of(2026, 8, 10, 12, 0));
        Order delivered = persistOrder(customer, burgerPlace);
        delivered.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered, LocalDateTime.of(2026, 8, 11, 12, 0));
        entityManager.flush();
        entityManager.clear();

        List<OrderStatusCount> result = orderRepository.countGroupByStatusInRange(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).extracting(OrderStatusCount::status, OrderStatusCount::count)
                .containsExactlyInAnyOrder(tuple(OrderStatus.NEW, 1L), tuple(OrderStatus.DELIVERED, 1L));
    }

    @Test
    void countGroupByCityInRange_groupsByDeliveryCity_inRangeOnly() {
        User customer = persistUser("admin-city-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order cairoOrder1 = persistOrder(customer, restaurant);
        Order cairoOrder2 = persistOrder(customer, restaurant);
        Order gizaOrder = persistOrder(customer, restaurant);
        gizaOrder.setDeliveryCity("Giza");
        Order outOfRange = persistOrder(customer, restaurant);
        setCreatedAt(cairoOrder1, LocalDateTime.of(2026, 8, 10, 12, 0));
        setCreatedAt(cairoOrder2, LocalDateTime.of(2026, 8, 11, 12, 0));
        setCreatedAt(gizaOrder, LocalDateTime.of(2026, 8, 12, 12, 0));
        setCreatedAt(outOfRange, LocalDateTime.of(2026, 9, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        List<CityOrderCount> result = orderRepository.countGroupByCityInRange(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).extracting(CityOrderCount::city, CityOrderCount::count)
                .containsExactlyInAnyOrder(tuple("Cairo", 2L), tuple("Giza", 1L));
    }

    @Test
    void findCreatedAtInRange_returnsOneTimestampPerOrder_inRangeOnly() {
        User customer = persistUser("admin-timestamps-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order inRange = persistOrder(customer, restaurant);
        setCreatedAt(inRange, LocalDateTime.of(2026, 8, 10, 9, 0));
        Order outOfRange = persistOrder(customer, restaurant);
        setCreatedAt(outOfRange, LocalDateTime.of(2026, 9, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        List<LocalDateTime> result = orderRepository.findCreatedAtInRange(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).containsExactly(LocalDateTime.of(2026, 8, 10, 9, 0));
    }

    @Test
    void findRevenueLinesByStatusInRange_returnsOnlyTheGivenStatus_acrossRestaurantsInRange() {
        User customer = persistUser("admin-lines-" + System.nanoTime() + "@example.com");
        Restaurant pizzaPlace = persistRestaurant("Pizza Place");
        Restaurant burgerPlace = persistRestaurant("Burger Place");
        Order delivered1 = persistOrder(customer, pizzaPlace);
        delivered1.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered1, LocalDateTime.of(2026, 8, 10, 9, 0));
        Order delivered2 = persistOrder(customer, burgerPlace);
        delivered2.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered2, LocalDateTime.of(2026, 8, 11, 9, 0));
        Order cancelled = persistOrder(customer, pizzaPlace);
        cancelled.setStatus(OrderStatus.CANCELLED);
        setCreatedAt(cancelled, LocalDateTime.of(2026, 8, 10, 10, 0));
        entityManager.flush();
        entityManager.clear();

        List<RevenueLine> result = orderRepository.findRevenueLinesByStatusInRange(
                OrderStatus.DELIVERED, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).total()).isEqualByComparingTo(BigDecimal.valueOf(110));
    }

    @Test
    void countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan_countsEveryStatus_inRangeOnly() {
        User customer = persistUser("analytics-count-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order inRangeCancelled = persistOrder(customer, restaurant);
        inRangeCancelled.setStatus(OrderStatus.CANCELLED);
        Order inRangeNew = persistOrder(customer, restaurant);
        setCreatedAt(inRangeCancelled, LocalDateTime.of(2026, 8, 10, 12, 0));
        setCreatedAt(inRangeNew, LocalDateTime.of(2026, 8, 15, 8, 0));
        Order outOfRange = persistOrder(customer, restaurant);
        setCreatedAt(outOfRange, LocalDateTime.of(2026, 9, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        long count = orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                restaurant.getId(), LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void sumRevenueByRestaurantAndStatusInRange_sumsOnlyTheGivenStatus_inRange() {
        User customer = persistUser("analytics-revenue-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order delivered1 = persistOrder(customer, restaurant);
        delivered1.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered1, LocalDateTime.of(2026, 8, 10, 12, 0));
        Order delivered2 = persistOrder(customer, restaurant);
        delivered2.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(delivered2, LocalDateTime.of(2026, 8, 20, 12, 0));
        Order cancelled = persistOrder(customer, restaurant);
        cancelled.setStatus(OrderStatus.CANCELLED);
        setCreatedAt(cancelled, LocalDateTime.of(2026, 8, 12, 12, 0));
        entityManager.flush();
        entityManager.clear();

        RevenueAggregate result = orderRepository.sumRevenueByRestaurantAndStatusInRange(restaurant.getId(),
                OrderStatus.DELIVERED, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result.orderCount()).isEqualTo(2);
        assertThat(result.totalRevenueOrZero()).isEqualByComparingTo(BigDecimal.valueOf(220));
    }

    @Test
    void sumRevenueByRestaurantAndStatusInRange_returnsZeroRevenueAndCount_whenNothingMatches() {
        Restaurant restaurant = persistRestaurant("Empty Place");

        RevenueAggregate result = orderRepository.sumRevenueByRestaurantAndStatusInRange(restaurant.getId(),
                OrderStatus.DELIVERED, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result.orderCount()).isZero();
        assertThat(result.totalRevenueOrZero()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void countByRestaurantIdGroupByStatusInRange_groupsByStatus_omittingStatusesWithNoOrders() {
        User customer = persistUser("analytics-status-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order newOrder = persistOrder(customer, restaurant);
        setCreatedAt(newOrder, LocalDateTime.of(2026, 8, 10, 12, 0));
        Order deliveredOrder = persistOrder(customer, restaurant);
        deliveredOrder.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(deliveredOrder, LocalDateTime.of(2026, 8, 11, 12, 0));
        Order anotherDelivered = persistOrder(customer, restaurant);
        anotherDelivered.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(anotherDelivered, LocalDateTime.of(2026, 8, 12, 12, 0));
        entityManager.flush();
        entityManager.clear();

        List<OrderStatusCount> result = orderRepository.countByRestaurantIdGroupByStatusInRange(
                restaurant.getId(), LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).extracting(OrderStatusCount::status, OrderStatusCount::count)
                .containsExactlyInAnyOrder(tuple(OrderStatus.NEW, 1L), tuple(OrderStatus.DELIVERED, 2L));
    }

    @Test
    void findRevenueLinesByRestaurantAndStatusInRange_returnsOnlyTheGivenStatus_inRange() {
        User customer = persistUser("analytics-lines-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("Pizza Place");
        Order deliveredMorning = persistOrder(customer, restaurant);
        deliveredMorning.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(deliveredMorning, LocalDateTime.of(2026, 8, 10, 9, 0));
        Order deliveredEvening = persistOrder(customer, restaurant);
        deliveredEvening.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(deliveredEvening, LocalDateTime.of(2026, 8, 10, 21, 0));
        Order deliveredNextDay = persistOrder(customer, restaurant);
        deliveredNextDay.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(deliveredNextDay, LocalDateTime.of(2026, 8, 11, 9, 0));
        Order cancelledSameDay = persistOrder(customer, restaurant);
        cancelledSameDay.setStatus(OrderStatus.CANCELLED);
        setCreatedAt(cancelledSameDay, LocalDateTime.of(2026, 8, 10, 10, 0));
        Order outOfRange = persistOrder(customer, restaurant);
        outOfRange.setStatus(OrderStatus.DELIVERED);
        setCreatedAt(outOfRange, LocalDateTime.of(2026, 9, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        List<RevenueLine> result = orderRepository.findRevenueLinesByRestaurantAndStatusInRange(restaurant.getId(),
                OrderStatus.DELIVERED, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(RevenueLine::createdAt).containsExactlyInAnyOrder(
                LocalDateTime.of(2026, 8, 10, 9, 0), LocalDateTime.of(2026, 8, 10, 21, 0),
                LocalDateTime.of(2026, 8, 11, 9, 0));
        assertThat(result.get(0).total()).isEqualByComparingTo(BigDecimal.valueOf(110));
    }

    /** {@code createdAt} is {@code @CreationTimestamp}-generated, so date-range tests must overwrite it directly. */
    private void setCreatedAt(Order order, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE orders SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
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
        order.setStatus(OrderStatus.NEW);
        entityManager.persist(order);
        entityManager.flush();
        return order;
    }
}
