package com.food.foodapp.order.service;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.order.dto.DailyRevenueResponse;
import com.food.foodapp.order.dto.OrderStatusCountResponse;
import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.order.repository.OrderStatusCount;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.order.repository.RevenueLine;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantOwnershipGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Restaurant-owner analytics: the dashboard overview's KPI cards (current-month order volume and
 * delivered revenue, each with a trend against the equivalent slice of the previous month) and the
 * day-by-day revenue chart for an arbitrary date range.
 * <p>
 * <b>Revenue recognition:</b> every revenue figure here — the overview KPI, its trend, and the
 * chart's daily bars and period totals — counts only {@link OrderStatus#DELIVERED} orders. An
 * order still in progress ({@code NEW}/{@code CONFIRMED}/{@code PREPARING}/{@code ON_THE_WAY})
 * hasn't actually earned the restaurant anything yet and can still be cancelled before delivery,
 * and a {@code CANCELLED} order never will; counting either would make "revenue" overstate money
 * the restaurant has actually been paid for. "Total orders" and "orders by status", by contrast,
 * count every status — they describe order *volume*, not money earned.
 * <p>
 * Scoped to {@code restaurantId} the same way {@code OrderService}'s other owner-facing methods
 * are (see {@code OwnerOrderController}): every query filters on {@code restaurant.id}, and both
 * public methods first call {@code RestaurantOwnershipGuard.requireOwnedRestaurant}, so a caller
 * who does not own {@code restaurantId} gets {@code 403} and can never see another restaurant's
 * financial data.
 */
@Service
@RequiredArgsConstructor
public class OrderAnalyticsService {

    /** Revenue is recognized only once an order reaches this state — see the class javadoc. */
    private static final OrderStatus REVENUE_STATUS = OrderStatus.DELIVERED;

    /** A generous cap on {@code /revenue}'s date range so a caller can't force a multi-year, day-by-day zero-filled response. */
    private static final long MAX_REVENUE_RANGE_DAYS = 366;

    private final OrderRepository orderRepository;
    private final RestaurantOwnershipGuard ownershipGuard;

    /**
     * The overview's two KPI cards (with month-over-month trend percentages) plus the
     * forward-looking completed-order-count/average-order-value/orders-by-status fields, all
     * scoped to the current calendar month.
     */
    @Transactional(readOnly = true)
    public OwnerAnalyticsOverviewResponse getOverview(Long restaurantId) {
        Restaurant restaurant = ownershipGuard.requireOwnedRestaurant(restaurantId);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDateTime currentFrom = monthStart.atStartOfDay();
        LocalDateTime currentTo = today.plusDays(1).atStartOfDay();

        LocalDate previousMonthStart = monthStart.minusMonths(1);
        // Compares the same number of elapsed days against last month, capped at its own length, so a
        // trend computed on (e.g.) day 3 of the month never pits 3 days of orders against a full
        // 28-31 day previous month, which would make growth look catastrophic for no real reason.
        LocalDate previousToDate = previousMonthStart.plusDays(today.getDayOfMonth());
        if (previousToDate.isAfter(monthStart)) {
            previousToDate = monthStart;
        }
        LocalDateTime previousFrom = previousMonthStart.atStartOfDay();
        LocalDateTime previousTo = previousToDate.atStartOfDay();

        long totalOrders = orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                restaurantId, currentFrom, currentTo);
        long previousTotalOrders = orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                restaurantId, previousFrom, previousTo);

        RevenueAggregate currentRevenue = orderRepository.sumRevenueByRestaurantAndStatusInRange(
                restaurantId, REVENUE_STATUS, currentFrom, currentTo);
        RevenueAggregate previousRevenue = orderRepository.sumRevenueByRestaurantAndStatusInRange(
                restaurantId, REVENUE_STATUS, previousFrom, previousTo);

        return OwnerAnalyticsOverviewResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .periodStart(monthStart)
                .periodEnd(today)
                .totalOrders(totalOrders)
                .totalOrdersTrendPercentage(
                        percentageChange(BigDecimal.valueOf(totalOrders), BigDecimal.valueOf(previousTotalOrders)))
                .revenue(currentRevenue.totalRevenueOrZero())
                .revenueTrendPercentage(
                        percentageChange(currentRevenue.totalRevenueOrZero(), previousRevenue.totalRevenueOrZero()))
                .completedOrders(currentRevenue.orderCount())
                .averageOrderValue(averageOrderValue(currentRevenue))
                .ordersByStatus(ordersByStatus(restaurantId, currentFrom, currentTo))
                .build();
    }

    /**
     * The revenue chart's day-by-day series for {@code [from, to]} (both inclusive), plus a
     * period-over-period {@link OwnerRevenueAnalyticsResponse#getChangePercentage()} against the
     * immediately preceding range of the same length. A caller that omits both bounds gets the
     * current Saturday-to-Friday week — see {@link #resolveRange} — which makes that badge a
     * week-over-week change, matching the owner dashboard's default chart.
     */
    @Transactional(readOnly = true)
    public OwnerRevenueAnalyticsResponse getRevenue(Long restaurantId, LocalDate from, LocalDate to) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        LocalDate[] range = resolveRange(from, to);
        LocalDate rangeFrom = range[0];
        LocalDate rangeTo = range[1];

        long rangeLengthDays = ChronoUnit.DAYS.between(rangeFrom, rangeTo) + 1;
        LocalDateTime currentFrom = rangeFrom.atStartOfDay();
        LocalDateTime currentTo = rangeTo.plusDays(1).atStartOfDay();
        LocalDateTime previousFrom = rangeFrom.minusDays(rangeLengthDays).atStartOfDay();
        LocalDateTime previousTo = currentFrom;

        RevenueAggregate currentRevenue = orderRepository.sumRevenueByRestaurantAndStatusInRange(
                restaurantId, REVENUE_STATUS, currentFrom, currentTo);
        RevenueAggregate previousRevenue = orderRepository.sumRevenueByRestaurantAndStatusInRange(
                restaurantId, REVENUE_STATUS, previousFrom, previousTo);

        List<RevenueLine> lines = orderRepository.findRevenueLinesByRestaurantAndStatusInRange(
                restaurantId, REVENUE_STATUS, currentFrom, currentTo);

        return OwnerRevenueAnalyticsResponse.builder()
                .restaurantId(restaurantId)
                .from(rangeFrom)
                .to(rangeTo)
                .totalRevenue(currentRevenue.totalRevenueOrZero())
                .previousPeriodRevenue(previousRevenue.totalRevenueOrZero())
                .changePercentage(
                        percentageChange(currentRevenue.totalRevenueOrZero(), previousRevenue.totalRevenueOrZero()))
                .dailyRevenue(zeroFillDailyRevenue(rangeFrom, rangeTo, lines))
                .build();
    }

    /**
     * Resolves the caller's optional {@code from}/{@code to} into a concrete range: both present
     * (validated for order and span), both absent (the current Saturday-to-Friday week — the
     * Arab-world week start the owner dashboard's chart uses), or exactly one present, which is
     * rejected rather than silently guessing the other bound.
     */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return currentWeekSaturdayToFriday();
        }
        if (from == null || to == null) {
            throw new InvalidRequestParameterException(
                    "Query parameters 'from' and 'to' must both be given, or both omitted");
        }
        if (from.isAfter(to)) {
            throw new InvalidRequestParameterException("Query parameter 'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_REVENUE_RANGE_DAYS) {
            throw new InvalidRequestParameterException(
                    "Date range must not exceed " + MAX_REVENUE_RANGE_DAYS + " days");
        }
        return new LocalDate[] {from, to};
    }

    /** Saturday of the current week through the following Friday — see {@link #resolveRange}. */
    private LocalDate[] currentWeekSaturdayToFriday() {
        LocalDate today = LocalDate.now();
        int daysSinceSaturday = Math.floorMod(today.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue(), 7);
        LocalDate weekStart = today.minusDays(daysSinceSaturday);
        return new LocalDate[] {weekStart, weekStart.plusDays(6)};
    }

    /**
     * Buckets {@code lines} by calendar day (see {@link RevenueLine} for why this happens in Java
     * rather than a JPQL {@code GROUP BY}) and produces one entry per day in {@code [from, to]},
     * zero-filling days with no delivered orders so the chart never has a gap.
     */
    private List<DailyRevenueResponse> zeroFillDailyRevenue(LocalDate from, LocalDate to, List<RevenueLine> lines) {
        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        Map<LocalDate, Long> orderCountByDay = new HashMap<>();
        for (RevenueLine line : lines) {
            LocalDate day = line.createdAt().toLocalDate();
            revenueByDay.merge(day, line.total(), BigDecimal::add);
            orderCountByDay.merge(day, 1L, Long::sum);
        }

        List<DailyRevenueResponse> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            result.add(DailyRevenueResponse.builder()
                    .date(day)
                    .revenue(revenueByDay.getOrDefault(day, BigDecimal.ZERO))
                    .orderCount(orderCountByDay.getOrDefault(day, 0L))
                    .build());
        }
        return result;
    }

    /** Every {@link OrderStatus}, zero-filling whichever ones {@code countByRestaurantIdGroupByStatusInRange} found no orders for. */
    private List<OrderStatusCountResponse> ordersByStatus(Long restaurantId, LocalDateTime from, LocalDateTime to) {
        Map<OrderStatus, Long> counts = orderRepository.countByRestaurantIdGroupByStatusInRange(restaurantId, from, to)
                .stream().collect(Collectors.toMap(OrderStatusCount::status, OrderStatusCount::count));

        return Arrays.stream(OrderStatus.values())
                .map(status -> OrderStatusCountResponse.builder()
                        .status(status)
                        .count(counts.getOrDefault(status, 0L))
                        .build())
                .toList();
    }

    private BigDecimal averageOrderValue(RevenueAggregate revenue) {
        if (revenue.orderCount() == 0) {
            return BigDecimal.ZERO;
        }
        return revenue.totalRevenueOrZero().divide(BigDecimal.valueOf(revenue.orderCount()), 2, RoundingMode.HALF_UP);
    }

    /**
     * {@code null} means "no previous-period activity to compare against" (a KPI card should show
     * "new"/"—" rather than a misleading percentage) — distinct from a genuine {@code 0} previous
     * value paired with a genuine {@code 0} current value, which is a real, reportable 0% change.
     */
    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }
}
