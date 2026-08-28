package com.food.foodapp.order.service;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.order.dto.AdminAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.AdminOrdersAnalyticsResponse;
import com.food.foodapp.order.dto.AdminOrdersByCityResponse;
import com.food.foodapp.order.dto.AdminRevenueAnalyticsResponse;
import com.food.foodapp.order.dto.CityOrderShareResponse;
import com.food.foodapp.order.dto.DailyOrderCountResponse;
import com.food.foodapp.order.dto.DailyRevenueResponse;
import com.food.foodapp.order.dto.OrderStatusCountResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.repository.CityOrderCount;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.order.repository.OrderStatusCount;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.order.repository.RevenueLine;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Platform-wide admin analytics: the admin dashboard's default landing tab. Covers the 4 KPI
 * cards (with period-over-period trend percentages), the day-by-day orders and revenue charts,
 * and the orders-by-city breakdown — all aggregated directly from persisted order/restaurant/user
 * data (see {@link OrderRepository}, {@link RestaurantRepository}, {@link UserRepository}), never
 * a separate analytics table.
 * <p>
 * <b>Revenue recognition:</b> exactly like the owner dashboard's analytics, every revenue figure
 * here counts only {@link OrderStatus#DELIVERED} orders — see {@link #REVENUE_STATUS}. "Total
 * orders" and "orders by status" count every status, since they describe order *volume*, not
 * money earned.
 * <p>
 * NOTE: same authorization gap as {@code AdminUserController}/{@code AdminRestaurantController}
 * — this codebase has no admin-authentication middleware yet (no {@code ADMIN} role, no Spring
 * Security), so these endpoints are not yet gated to an authenticated admin.
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    /** Revenue is recognized only once an order reaches this state — see the class javadoc. */
    private static final OrderStatus REVENUE_STATUS = OrderStatus.DELIVERED;

    /** A generous cap on an explicit {@code from}/{@code to} range so a caller can't force a multi-year, day-by-day zero-filled response. */
    private static final long MAX_RANGE_DAYS = 366;

    /** The orders-by-city donut shows at most this many individual cities; the rest are collapsed into "Other". */
    private static final int CITY_BUCKET_LIMIT = 5;

    private static final String OTHER_CITY_BUCKET = "Other";

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    /**
     * The 4 KPI cards for one of the dashboard's period presets, each with a period-over-period
     * trend against the immediately preceding period of the same length — except {@code period =
     * "1y"} ("this year"), whose trend compares against the same Jan-1-to-date span one calendar
     * year earlier (year-over-year), which is a far more meaningful comparison for a "this year"
     * KPI than pitting a handful of months against the tail end of last December. See
     * {@link #resolvePeriod} for exactly how each preset resolves.
     */
    @Transactional(readOnly = true)
    public AdminAnalyticsOverviewResponse getOverview(String period) {
        ResolvedPeriod resolved = resolvePeriod(period);

        long totalOrders = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                resolved.currentFrom(), resolved.currentTo());
        long previousTotalOrders = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                resolved.previousFrom(), resolved.previousTo());

        RevenueAggregate currentRevenue = orderRepository.sumRevenueByStatusInRange(
                REVENUE_STATUS, resolved.currentFrom(), resolved.currentTo());
        RevenueAggregate previousRevenue = orderRepository.sumRevenueByStatusInRange(
                REVENUE_STATUS, resolved.previousFrom(), resolved.previousTo());

        long activeRestaurants = restaurantRepository.countByApprovalStatusAndCreatedAtLessThan(
                RestaurantApprovalStatus.APPROVED, resolved.currentTo());
        long previousActiveRestaurants = restaurantRepository.countByApprovalStatusAndCreatedAtLessThan(
                RestaurantApprovalStatus.APPROVED, resolved.previousTo());

        long registeredCustomers = userRepository.countByRoleAndCreatedAtLessThan(Role.CUSTOMER, resolved.currentTo());
        long previousRegisteredCustomers =
                userRepository.countByRoleAndCreatedAtLessThan(Role.CUSTOMER, resolved.previousTo());

        return AdminAnalyticsOverviewResponse.builder()
                .period(resolved.label())
                .periodStart(resolved.periodStart())
                .periodEnd(resolved.periodEnd())
                .totalOrders(totalOrders)
                .totalOrdersTrendPercentage(
                        percentageChange(BigDecimal.valueOf(totalOrders), BigDecimal.valueOf(previousTotalOrders)))
                .totalRevenue(currentRevenue.totalRevenueOrZero())
                .totalRevenueTrendPercentage(
                        percentageChange(currentRevenue.totalRevenueOrZero(), previousRevenue.totalRevenueOrZero()))
                .activeRestaurants(activeRestaurants)
                .activeRestaurantsTrendPercentage(percentageChange(
                        BigDecimal.valueOf(activeRestaurants), BigDecimal.valueOf(previousActiveRestaurants)))
                .registeredCustomers(registeredCustomers)
                .registeredCustomersTrendPercentage(percentageChange(
                        BigDecimal.valueOf(registeredCustomers), BigDecimal.valueOf(previousRegisteredCustomers)))
                .averageOrderValue(averageOrderValue(currentRevenue))
                .ordersByStatus(ordersByStatus(resolved.currentFrom(), resolved.currentTo()))
                .build();
    }

    /**
     * The admin dashboard's day-by-day orders bar chart for {@code [from, to]} (both inclusive),
     * plus a period-over-period {@link AdminOrdersAnalyticsResponse#getChangePercentage()}
     * against the immediately preceding range of the same length. A caller that omits both
     * bounds gets the current Monday-to-Sunday week — see {@link #resolveRange} — matching the
     * dashboard's default chart. Counts every order regardless of status: this chart is about
     * order *volume*, not fulfilled revenue.
     */
    @Transactional(readOnly = true)
    public AdminOrdersAnalyticsResponse getOrders(LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        LocalDate rangeFrom = range[0];
        LocalDate rangeTo = range[1];

        long rangeLengthDays = ChronoUnit.DAYS.between(rangeFrom, rangeTo) + 1;
        LocalDateTime currentFrom = rangeFrom.atStartOfDay();
        LocalDateTime currentTo = rangeTo.plusDays(1).atStartOfDay();
        LocalDateTime previousFrom = rangeFrom.minusDays(rangeLengthDays).atStartOfDay();
        LocalDateTime previousTo = currentFrom;

        long currentTotal = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(currentFrom, currentTo);
        long previousTotal = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(previousFrom, previousTo);

        List<LocalDateTime> timestamps = orderRepository.findCreatedAtInRange(currentFrom, currentTo);

        return AdminOrdersAnalyticsResponse.builder()
                .from(rangeFrom)
                .to(rangeTo)
                .totalOrders(currentTotal)
                .changePercentage(percentageChange(BigDecimal.valueOf(currentTotal), BigDecimal.valueOf(previousTotal)))
                .dailyOrders(zeroFillDailyOrders(rangeFrom, rangeTo, timestamps))
                .build();
    }

    /**
     * The admin dashboard's day-by-day revenue chart for {@code [from, to]} (both inclusive),
     * plus a period-over-period {@link AdminRevenueAnalyticsResponse#getChangePercentage()}
     * against the immediately preceding range of the same length. Defaults to the current
     * Monday-to-Sunday week when both bounds are omitted, the same as {@link #getOrders}.
     */
    @Transactional(readOnly = true)
    public AdminRevenueAnalyticsResponse getRevenue(LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        LocalDate rangeFrom = range[0];
        LocalDate rangeTo = range[1];

        long rangeLengthDays = ChronoUnit.DAYS.between(rangeFrom, rangeTo) + 1;
        LocalDateTime currentFrom = rangeFrom.atStartOfDay();
        LocalDateTime currentTo = rangeTo.plusDays(1).atStartOfDay();
        LocalDateTime previousFrom = rangeFrom.minusDays(rangeLengthDays).atStartOfDay();
        LocalDateTime previousTo = currentFrom;

        RevenueAggregate currentRevenue = orderRepository.sumRevenueByStatusInRange(REVENUE_STATUS, currentFrom, currentTo);
        RevenueAggregate previousRevenue = orderRepository.sumRevenueByStatusInRange(REVENUE_STATUS, previousFrom, previousTo);

        List<RevenueLine> lines = orderRepository.findRevenueLinesByStatusInRange(REVENUE_STATUS, currentFrom, currentTo);

        return AdminRevenueAnalyticsResponse.builder()
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
     * The admin dashboard's orders-by-city donut/legend for {@code [from, to]} (both inclusive):
     * every city's share of the period's total orders, with all but the top
     * {@value #CITY_BUCKET_LIMIT} cities by order count collapsed into a single {@code "Other"}
     * entry. Defaults to the current Monday-to-Sunday week when both bounds are omitted, the same
     * as {@link #getOrders}. Counts every order regardless of status, matching {@link #getOrders}
     * — this is order volume by delivery location, not fulfilled revenue.
     */
    @Transactional(readOnly = true)
    public AdminOrdersByCityResponse getOrdersByCity(LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        LocalDate rangeFrom = range[0];
        LocalDate rangeTo = range[1];
        LocalDateTime currentFrom = rangeFrom.atStartOfDay();
        LocalDateTime currentTo = rangeTo.plusDays(1).atStartOfDay();

        List<CityOrderCount> counts = orderRepository.countGroupByCityInRange(currentFrom, currentTo);
        long totalOrders = counts.stream().mapToLong(CityOrderCount::count).sum();

        return AdminOrdersByCityResponse.builder()
                .from(rangeFrom)
                .to(rangeTo)
                .totalOrders(totalOrders)
                .cities(bucketCities(counts, totalOrders))
                .build();
    }

    /**
     * Sorts {@code counts} descending, keeps the top {@value #CITY_BUCKET_LIMIT} as individual
     * entries, and collapses the remainder into one {@code "Other"} entry — omitted entirely when
     * there is no long tail to collapse. Each entry's percentage is of {@code totalOrders}, scaled
     * to 2 decimal places; when {@code totalOrders} is zero every percentage is zero rather than
     * a division-by-zero.
     */
    private List<CityOrderShareResponse> bucketCities(List<CityOrderCount> counts, long totalOrders) {
        List<CityOrderCount> sorted = counts.stream()
                .sorted(Comparator.comparingLong(CityOrderCount::count).reversed())
                .toList();

        List<CityOrderCount> top = sorted.size() > CITY_BUCKET_LIMIT ? sorted.subList(0, CITY_BUCKET_LIMIT) : sorted;
        long otherCount = sorted.stream().skip(top.size()).mapToLong(CityOrderCount::count).sum();

        List<CityOrderShareResponse> result = new ArrayList<>();
        for (CityOrderCount cityCount : top) {
            result.add(CityOrderShareResponse.builder()
                    .city(cityCount.city())
                    .orderCount(cityCount.count())
                    .percentage(sharePercentage(cityCount.count(), totalOrders))
                    .build());
        }
        if (otherCount > 0) {
            result.add(CityOrderShareResponse.builder()
                    .city(OTHER_CITY_BUCKET)
                    .orderCount(otherCount)
                    .percentage(sharePercentage(otherCount, totalOrders))
                    .build());
        }
        return result;
    }

    private BigDecimal sharePercentage(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * Resolves the {@code period} query parameter into concrete current/previous date ranges.
     * <ul>
     *     <li>{@code "7d"} — the last 7 days including today; previous = the immediately
     *     preceding 7-day block.</li>
     *     <li>{@code "30d"} — the last 30 days including today; previous = the immediately
     *     preceding 30-day block.</li>
     *     <li>{@code "1y"} — the current calendar year to date (Jan 1 through today); previous =
     *     the same Jan-1-to-date span one calendar year earlier (year-over-year), capped
     *     defensively at one year long the same way the owner dashboard's month-over-month KPI
     *     caps its previous-month span at the current day-of-month.</li>
     * </ul>
     * Any other value is rejected rather than silently defaulted; a blank/absent value defaults
     * to {@code "7d"}, matching the dashboard's default landing view.
     */
    private ResolvedPeriod resolvePeriod(String rawPeriod) {
        String normalized = (rawPeriod == null || rawPeriod.isBlank()) ? "7d" : rawPeriod.trim().toLowerCase();
        LocalDate today = LocalDate.now();

        return switch (normalized) {
            case "7d" -> trailingDaysPeriod(normalized, today, 7);
            case "30d" -> trailingDaysPeriod(normalized, today, 30);
            case "1y" -> yearToDatePeriod(normalized, today);
            default -> throw new InvalidRequestParameterException(
                    "Invalid 'period' value: '" + rawPeriod + "'. Allowed values: 7d, 30d, 1y");
        };
    }

    private ResolvedPeriod trailingDaysPeriod(String label, LocalDate today, int days) {
        LocalDate periodStart = today.minusDays(days - 1L);
        LocalDateTime currentFrom = periodStart.atStartOfDay();
        LocalDateTime currentTo = today.plusDays(1).atStartOfDay();
        LocalDateTime previousFrom = currentFrom.minusDays(days);
        LocalDateTime previousTo = currentFrom;
        return new ResolvedPeriod(label, periodStart, today, currentFrom, currentTo, previousFrom, previousTo);
    }

    private ResolvedPeriod yearToDatePeriod(String label, LocalDate today) {
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDateTime currentFrom = yearStart.atStartOfDay();
        LocalDateTime currentTo = today.plusDays(1).atStartOfDay();

        LocalDate previousYearStart = yearStart.minusYears(1);
        LocalDate previousToDate = previousYearStart.plusDays(ChronoUnit.DAYS.between(yearStart, today));
        LocalDate previousYearEnd = previousYearStart.plusYears(1);
        if (previousToDate.isAfter(previousYearEnd)) {
            previousToDate = previousYearEnd;
        }
        LocalDateTime previousFrom = previousYearStart.atStartOfDay();
        LocalDateTime previousTo = previousToDate.atStartOfDay();
        return new ResolvedPeriod(label, yearStart, today, currentFrom, currentTo, previousFrom, previousTo);
    }

    /**
     * Resolves an explicit {@code from}/{@code to} into a concrete range: both present
     * (validated for order and span), both absent (the current Monday-to-Sunday week — the
     * canonical week-start convention picked for the admin dashboard's charts; the owner
     * dashboard's chart independently defaults to Saturday-to-Friday, and the two are not meant
     * to agree — each dashboard's chart is its own fixed weekly view), or exactly one present,
     * which is rejected rather than silently guessing the other bound.
     */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return currentWeekMondayToSunday();
        }
        if (from == null || to == null) {
            throw new InvalidRequestParameterException(
                    "Query parameters 'from' and 'to' must both be given, or both omitted");
        }
        if (from.isAfter(to)) {
            throw new InvalidRequestParameterException("Query parameter 'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidRequestParameterException("Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDate[] {from, to};
    }

    /** Monday of the current week through the following Sunday — see {@link #resolveRange}. */
    private LocalDate[] currentWeekMondayToSunday() {
        LocalDate today = LocalDate.now();
        int daysSinceMonday = Math.floorMod(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue(), 7);
        LocalDate weekStart = today.minusDays(daysSinceMonday);
        return new LocalDate[] {weekStart, weekStart.plusDays(6)};
    }

    /**
     * Buckets {@code timestamps} by calendar day and produces one entry per day in
     * {@code [from, to]}, zero-filling days with no orders so the chart never has a gap.
     */
    private List<DailyOrderCountResponse> zeroFillDailyOrders(LocalDate from, LocalDate to, List<LocalDateTime> timestamps) {
        Map<LocalDate, Long> countByDay = new HashMap<>();
        for (LocalDateTime timestamp : timestamps) {
            countByDay.merge(timestamp.toLocalDate(), 1L, Long::sum);
        }

        List<DailyOrderCountResponse> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            result.add(DailyOrderCountResponse.builder()
                    .date(day)
                    .orderCount(countByDay.getOrDefault(day, 0L))
                    .build());
        }
        return result;
    }

    /**
     * Buckets {@code lines} by calendar day and produces one entry per day in {@code [from, to]},
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

    /** Every {@link OrderStatus}, zero-filling whichever ones {@code countGroupByStatusInRange} found no orders for. */
    private List<OrderStatusCountResponse> ordersByStatus(LocalDateTime from, LocalDateTime to) {
        Map<OrderStatus, Long> counts = orderRepository.countGroupByStatusInRange(from, to)
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

    /** The concrete bounds one {@code period} preset resolved to — see {@link #resolvePeriod}. */
    private record ResolvedPeriod(
            String label,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDateTime currentFrom,
            LocalDateTime currentTo,
            LocalDateTime previousFrom,
            LocalDateTime previousTo) {
    }
}
