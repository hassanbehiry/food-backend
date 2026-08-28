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

    /**
     * The admin analytics overview's "total orders" KPI: every order placed platform-wide in the
     * range, regardless of status (including {@code CANCELLED}) — order *volume*, not fulfilled
     * revenue, mirroring the restaurant-scoped
     * {@code countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan} the owner
     * dashboard's analytics uses. {@code to} is exclusive, matching every other date-range query
     * in this repository.
     */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    /**
     * Total revenue and order count platform-wide for one status/date-range — the aggregate
     * behind the admin analytics overview's "revenue" KPI and the admin revenue chart's period
     * totals. Always called with {@code status = OrderStatus.DELIVERED} in practice: revenue is
     * recognized only once an order is actually delivered, mirroring the restaurant-scoped
     * {@code sumRevenueByRestaurantAndStatusInRange} the owner dashboard's analytics uses.
     * {@code to} is exclusive.
     */
    @Query("SELECT new com.food.foodapp.order.repository.RevenueAggregate(SUM(o.total), COUNT(o)) "
            + "FROM Order o WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt < :to")
    RevenueAggregate sumRevenueByStatusInRange(
            @Param("status") OrderStatus status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Per-status order counts platform-wide within a date range, for the admin analytics
     * overview's "orders by status" breakdown. One query instead of one per status via
     * {@code GROUP BY}; a status with zero orders in the range has no row here, so the caller
     * zero-fills the rest. {@code to} is exclusive.
     */
    @Query("SELECT new com.food.foodapp.order.repository.OrderStatusCount(o.status, COUNT(o)) "
            + "FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to GROUP BY o.status")
    List<OrderStatusCount> countGroupByStatusInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Per-city order counts platform-wide within a date range, for the admin dashboard's
     * "orders by city" donut. Grouped directly by {@code deliveryCity} in SQL — unlike the
     * day-by-day revenue chart's bucketing (see {@link #findCreatedAtInRange}), this doesn't hit
     * Hibernate's record-projection-vs-truncated-date failure mode, since {@code deliveryCity} is
     * a plain string column, not a date expression. A city with zero orders in the range simply
     * has no row. {@code to} is exclusive.
     */
    @Query("SELECT new com.food.foodapp.order.repository.CityOrderCount(o.deliveryCity, COUNT(o)) "
            + "FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to GROUP BY o.deliveryCity")
    List<CityOrderCount> countGroupByCityInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * The raw order timestamps behind the admin dashboard's day-by-day orders bar chart, for
     * every order platform-wide in the range regardless of status (order volume, not revenue —
     * see {@link #countByCreatedAtGreaterThanEqualAndCreatedAtLessThan}). {@code
     * AdminAnalyticsService} buckets these by calendar day in application code rather than a
     * JPQL {@code GROUP BY CAST(... AS date)}: Hibernate's constructor-expression resolution for
     * a record projection fails to match the database's truncated-date column type back to a
     * {@code LocalDate} constructor parameter ({@code SemanticException: Missing constructor}),
     * the same failure mode the owner dashboard's revenue chart avoids the same way. A single
     * scalar column needs no record projection at all here. {@code to} is exclusive.
     */
    @Query("SELECT o.createdAt FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to")
    List<LocalDateTime> findCreatedAtInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * The delivered-order lines behind the admin revenue chart's day-by-day series, platform-wide
     * — {@code createdAt} and {@code total} only, not full entities. Always called with
     * {@code status = OrderStatus.DELIVERED} — see {@link #sumRevenueByStatusInRange}. {@code to}
     * is exclusive.
     */
    @Query("SELECT new com.food.foodapp.order.repository.RevenueLine(o.createdAt, o.total) "
            + "FROM Order o WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt < :to")
    List<RevenueLine> findRevenueLinesByStatusInRange(
            @Param("status") OrderStatus status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
