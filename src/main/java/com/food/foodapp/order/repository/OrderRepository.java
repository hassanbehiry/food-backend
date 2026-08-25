package com.food.foodapp.order.repository;

import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Scoped to {@code customerId} so an id belonging to another customer is indistinguishable
     * from one that doesn't exist at all — same ownership pattern {@code AddressRepository} uses.
     */
    @Query("SELECT o FROM Order o "
            + "LEFT JOIN FETCH o.items "
            + "LEFT JOIN FETCH o.restaurant "
            + "WHERE o.id = :id AND o.customer.id = :customerId")
    Optional<Order> findByIdAndCustomerIdWithItems(@Param("id") Long id, @Param("customerId") Long customerId);

    /**
     * Scoped to {@code restaurantId} so an id belonging to another restaurant is indistinguishable
     * from one that doesn't exist at all — same ownership pattern {@link #findByIdAndCustomerIdWithItems}
     * uses for customers, and the same "scoped to the path id, not an authenticated owner" gap
     * {@code MenuItemRepository#findByIdAndRestaurantId} already has (see {@code OwnerMenuItemController}).
     * Also join-fetches {@code customer}, unlike its customer-scoped sibling above: the owner
     * detail view this backs needs the customer's display name too (see
     * {@code OrderMapper#toOwnerResponse}).
     */
    @Query("SELECT o FROM Order o "
            + "LEFT JOIN FETCH o.items "
            + "LEFT JOIN FETCH o.restaurant "
            + "LEFT JOIN FETCH o.customer "
            + "WHERE o.id = :id AND o.restaurant.id = :restaurantId")
    Optional<Order> findByIdAndRestaurantIdWithItems(@Param("id") Long id, @Param("restaurantId") Long restaurantId);

    /**
     * The owner dashboard's orders table/tabs and "recent orders" panel: paginated, and
     * optionally filtered to one status — a {@code null} status is the "all" tab (every status,
     * unfiltered). Join-fetches {@code customer} only, not the {@code items} collection, so the
     * fetch join can't turn into Hibernate's "cannot simultaneously fetch multiple bags" error and
     * pagination stays backed by a single query per page instead of N+1 per-row lazy loads for the
     * customer name. An explicit {@code countQuery} avoids re-running the join for the count.
     */
    @Query(value = "SELECT o FROM Order o LEFT JOIN FETCH o.customer "
            + "WHERE o.restaurant.id = :restaurantId AND (:status IS NULL OR o.status = :status) "
            + "ORDER BY o.createdAt DESC, o.id DESC",
            countQuery = "SELECT COUNT(o) FROM Order o "
            + "WHERE o.restaurant.id = :restaurantId AND (:status IS NULL OR o.status = :status)")
    Page<Order> findByRestaurantIdAndOptionalStatus(
            @Param("restaurantId") Long restaurantId, @Param("status") OrderStatus status, Pageable pageable);

    /** Per-tab counts for the owner dashboard's status badges. */
    long countByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    /** The "all" tab's total, and the dashboard stats' denominator. */
    long countByRestaurantId(Long restaurantId);

    /**
     * The customer order-history table: paginated, newest first, and optionally narrowed by
     * status, restaurant, and/or a {@code createdAt} date range. {@code status}/{@code
     * restaurantId} are {@code NULL}-skips-the-check parameters the same way
     * {@link #findByRestaurantIdAndOptionalStatus} treats {@code status}. {@code fromDate}/{@code
     * toDate}, by contrast, are never {@code null} here — {@code OrderService} always resolves an
     * absent bound to a wide-open sentinel before calling this method, rather than this query
     * null-checking them the same way, because a bind parameter used only inside a {@code ? IS
     * NULL} check (with no other place in the query establishing its type) makes PostgreSQL's JDBC
     * driver fail with "could not determine data type of parameter" for a {@code timestamp}
     * parameter specifically — {@code status}/{@code restaurantId} don't hit this because Hibernate
     * can already resolve their type from the enum/id mapping. Join-fetches {@code restaurant}
     * only, not the {@code items} collection, for the same reason
     * {@link #findByRestaurantIdAndOptionalStatus} avoids fetching {@code customer} and {@code
     * items} together — {@link #sumItemQuantitiesByOrderIds} covers item counts separately. An
     * explicit {@code countQuery} avoids re-running the join for the count.
     */
    @Query(value = "SELECT o FROM Order o LEFT JOIN FETCH o.restaurant "
            + "WHERE o.customer.id = :customerId "
            + "AND (:status IS NULL OR o.status = :status) "
            + "AND (:restaurantId IS NULL OR o.restaurant.id = :restaurantId) "
            + "AND o.createdAt >= :fromDate AND o.createdAt < :toDate "
            + "ORDER BY o.createdAt DESC, o.id DESC",
            countQuery = "SELECT COUNT(o) FROM Order o "
            + "WHERE o.customer.id = :customerId "
            + "AND (:status IS NULL OR o.status = :status) "
            + "AND (:restaurantId IS NULL OR o.restaurant.id = :restaurantId) "
            + "AND o.createdAt >= :fromDate AND o.createdAt < :toDate")
    Page<Order> findByCustomerIdWithFilters(@Param("customerId") Long customerId,
            @Param("status") OrderStatus status, @Param("restaurantId") Long restaurantId,
            @Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate, Pageable pageable);

    /**
     * Total item quantity per order, for the ids in one page of {@link #findByCustomerIdWithFilters}
     * results — a second, small query rather than a fetch join, so paginating the order list can't
     * be broken by a to-many join multiplying rows. Orders with no matching id (none expected, since
     * every persisted order always has at least one item) simply have no entry in the result.
     */
    @Query("SELECT new com.food.foodapp.order.repository.OrderItemCount(oi.order.id, SUM(oi.quantity)) "
            + "FROM OrderItem oi WHERE oi.order.id IN :orderIds GROUP BY oi.order.id")
    List<OrderItemCount> sumItemQuantitiesByOrderIds(@Param("orderIds") List<Long> orderIds);
}
