package com.food.foodapp.order.service;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.order.dto.AdminAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.AdminOrdersAnalyticsResponse;
import com.food.foodapp.order.dto.AdminOrdersByCityResponse;
import com.food.foodapp.order.dto.AdminRevenueAnalyticsResponse;
import com.food.foodapp.order.dto.CityOrderShareResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.repository.CityOrderCount;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.order.repository.OrderStatusCount;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.order.repository.RevenueLine;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    private AdminAnalyticsService adminAnalyticsService;

    @BeforeEach
    void setUp() {
        adminAnalyticsService = new AdminAnalyticsService(orderRepository, restaurantRepository, userRepository);
    }

    @Test
    void getOverview_defaultsToLast7Days_whenPeriodOmitted() {
        stubOverviewRepositories(20L, 15L, BigDecimal.valueOf(500), 10L, BigDecimal.valueOf(400), 8L, 12L, 10L, 100L, 90L);

        AdminAnalyticsOverviewResponse response = adminAnalyticsService.getOverview(null);

        assertThat(response.getPeriod()).isEqualTo("7d");
        assertThat(response.getPeriodStart()).isEqualTo(LocalDate.now().minusDays(6));
        assertThat(response.getPeriodEnd()).isEqualTo(LocalDate.now());
    }

    @Test
    void getOverview_returnsKpisWithTrendPercentages_for7dPeriod() {
        stubOverviewRepositories(20L, 15L, BigDecimal.valueOf(500), 10L, BigDecimal.valueOf(400), 8L, 12L, 10L, 100L, 90L);
        when(orderRepository.countGroupByStatusInRange(any(), any()))
                .thenReturn(List.of(new OrderStatusCount(OrderStatus.DELIVERED, 10L), new OrderStatusCount(OrderStatus.NEW, 3L)));

        AdminAnalyticsOverviewResponse response = adminAnalyticsService.getOverview("7d");

        assertThat(response.getTotalOrders()).isEqualTo(20L);
        assertThat(response.getTotalOrdersTrendPercentage()).isEqualByComparingTo(BigDecimal.valueOf(33.33));
        assertThat(response.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(response.getTotalRevenueTrendPercentage()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        assertThat(response.getActiveRestaurants()).isEqualTo(12L);
        assertThat(response.getActiveRestaurantsTrendPercentage()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        assertThat(response.getRegisteredCustomers()).isEqualTo(100L);
        assertThat(response.getRegisteredCustomersTrendPercentage()).isEqualByComparingTo(BigDecimal.valueOf(11.11));
        assertThat(response.getAverageOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(response.getOrdersByStatus()).hasSize(OrderStatus.values().length);
        assertThat(response.getOrdersByStatus()).filteredOn(row -> row.getStatus() == OrderStatus.NEW)
                .extracting("count").containsExactly(3L);
        assertThat(response.getOrdersByStatus()).filteredOn(row -> row.getStatus() == OrderStatus.CANCELLED)
                .extracting("count").containsExactly(0L);
    }

    @Test
    void getOverview_returnsNullTrend_whenPreviousPeriodHadNoActivity() {
        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(5L, 0L);
        when(orderRepository.sumRevenueByStatusInRange(eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.valueOf(100), 2L), new RevenueAggregate(null, 0L));
        when(restaurantRepository.countByApprovalStatusAndCreatedAtLessThan(eq(RestaurantApprovalStatus.APPROVED), any()))
                .thenReturn(5L, 0L);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.CUSTOMER), any())).thenReturn(10L, 0L);
        when(orderRepository.countGroupByStatusInRange(any(), any())).thenReturn(List.of());

        AdminAnalyticsOverviewResponse response = adminAnalyticsService.getOverview("7d");

        assertThat(response.getTotalOrdersTrendPercentage()).isNull();
        assertThat(response.getTotalRevenueTrendPercentage()).isNull();
        assertThat(response.getActiveRestaurantsTrendPercentage()).isNull();
        assertThat(response.getRegisteredCustomersTrendPercentage()).isNull();
    }

    @Test
    void getOverview_returnsZeroTrendAndZeroAverage_whenCurrentAndPreviousAreBothEmpty() {
        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L, 0L);
        when(orderRepository.sumRevenueByStatusInRange(eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(null, 0L), new RevenueAggregate(null, 0L));
        when(restaurantRepository.countByApprovalStatusAndCreatedAtLessThan(eq(RestaurantApprovalStatus.APPROVED), any()))
                .thenReturn(0L, 0L);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.CUSTOMER), any())).thenReturn(0L, 0L);
        when(orderRepository.countGroupByStatusInRange(any(), any())).thenReturn(List.of());

        AdminAnalyticsOverviewResponse response = adminAnalyticsService.getOverview("30d");

        assertThat(response.getTotalOrdersTrendPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalRevenueTrendPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getActiveRestaurantsTrendPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getRegisteredCustomersTrendPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getAverageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getOverview_rejectsUnknownPeriodValue() {
        assertThatThrownBy(() -> adminAnalyticsService.getOverview("90d"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getOverview_resolves7dRange_asLast7DaysIncludingToday() {
        stubOverviewRepositories(0L, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L, 0L, 0L, 0L, 0L);
        when(orderRepository.countGroupByStatusInRange(any(), any())).thenReturn(List.of());

        adminAnalyticsService.getOverview("7d");

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, times(2))
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromCaptor.capture(), toCaptor.capture());

        LocalDate today = LocalDate.now();
        List<LocalDateTime> froms = fromCaptor.getAllValues();
        List<LocalDateTime> tos = toCaptor.getAllValues();
        assertThat(froms.get(0)).isEqualTo(today.minusDays(6).atStartOfDay());
        assertThat(tos.get(0)).isEqualTo(today.plusDays(1).atStartOfDay());
        assertThat(froms.get(1)).isEqualTo(today.minusDays(13).atStartOfDay());
        assertThat(tos.get(1)).isEqualTo(today.minusDays(6).atStartOfDay());
    }

    @Test
    void getOverview_resolves1yRange_asYearToDate_withYearOverYearPrevious() {
        stubOverviewRepositories(0L, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L, 0L, 0L, 0L, 0L);
        when(orderRepository.countGroupByStatusInRange(any(), any())).thenReturn(List.of());

        AdminAnalyticsOverviewResponse response = adminAnalyticsService.getOverview("1y");

        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withDayOfYear(1);
        assertThat(response.getPeriodStart()).isEqualTo(yearStart);
        assertThat(response.getPeriodEnd()).isEqualTo(today);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, times(2))
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromCaptor.capture(), toCaptor.capture());

        List<LocalDateTime> froms = fromCaptor.getAllValues();
        long elapsedDays = ChronoUnit.DAYS.between(yearStart, today);
        assertThat(froms.get(0)).isEqualTo(yearStart.atStartOfDay());
        assertThat(froms.get(1)).isEqualTo(yearStart.minusYears(1).atStartOfDay());
        assertThat(toCaptor.getAllValues().get(1)).isEqualTo(yearStart.minusYears(1).plusDays(elapsedDays).atStartOfDay());
    }

    @Test
    void getOrders_returnsDailyOrders_zeroFilledForDaysWithNoOrders() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 3);
        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(3L, 1L);
        when(orderRepository.findCreatedAtInRange(any(), any())).thenReturn(List.of(
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 1, 20, 0),
                LocalDateTime.of(2026, 8, 3, 12, 0)));

        AdminOrdersAnalyticsResponse response = adminAnalyticsService.getOrders(from, to);

        assertThat(response.getFrom()).isEqualTo(from);
        assertThat(response.getTo()).isEqualTo(to);
        assertThat(response.getTotalOrders()).isEqualTo(3L);
        assertThat(response.getChangePercentage()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(response.getDailyOrders()).hasSize(3);
        assertThat(response.getDailyOrders().get(0).getOrderCount()).isEqualTo(2);
        assertThat(response.getDailyOrders().get(1).getOrderCount()).isZero();
        assertThat(response.getDailyOrders().get(2).getOrderCount()).isEqualTo(1);
    }

    @Test
    void getOrders_defaultsToCurrentMondayToSundayWeek_whenBothBoundsOmitted() {
        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(orderRepository.findCreatedAtInRange(any(), any())).thenReturn(List.of());

        AdminOrdersAnalyticsResponse response = adminAnalyticsService.getOrders(null, null);

        assertThat(response.getFrom().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(response.getTo().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(ChronoUnit.DAYS.between(response.getFrom(), response.getTo())).isEqualTo(6);
        LocalDate today = LocalDate.now();
        assertThat(response.getFrom()).isBeforeOrEqualTo(today);
        assertThat(response.getTo()).isAfterOrEqualTo(today);
        assertThat(response.getDailyOrders()).hasSize(7);
    }

    @Test
    void getOrders_rejectsFromWithoutTo() {
        assertThatThrownBy(() -> adminAnalyticsService.getOrders(LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getOrders_rejectsFromAfterTo() {
        assertThatThrownBy(() -> adminAnalyticsService.getOrders(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getOrders_rejectsRangeExceedingMaxDays() {
        assertThatThrownBy(() -> adminAnalyticsService.getOrders(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getRevenue_returnsDailyRevenue_zeroFilledForDaysWithNoDeliveredOrders() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 3);
        when(orderRepository.sumRevenueByStatusInRange(eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.valueOf(150), 2L), new RevenueAggregate(BigDecimal.valueOf(100), 1L));
        when(orderRepository.findRevenueLinesByStatusInRange(eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(List.of(
                        new RevenueLine(LocalDateTime.of(2026, 8, 1, 12, 0), BigDecimal.valueOf(100)),
                        new RevenueLine(LocalDateTime.of(2026, 8, 1, 18, 0), BigDecimal.valueOf(50))));

        AdminRevenueAnalyticsResponse response = adminAnalyticsService.getRevenue(from, to);

        assertThat(response.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.getPreviousPeriodRevenue()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.getChangePercentage()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(response.getDailyRevenue()).hasSize(3);
        assertThat(response.getDailyRevenue().get(0).getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.getDailyRevenue().get(1).getRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getRevenue_queriesPreviousPeriod_asImmediatelyPrecedingRangeOfSameLength() {
        when(orderRepository.sumRevenueByStatusInRange(eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.ZERO, 0L));
        when(orderRepository.findRevenueLinesByStatusInRange(eq(OrderStatus.DELIVERED), any(), any())).thenReturn(List.of());

        adminAnalyticsService.getRevenue(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 28));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, times(2))
                .sumRevenueByStatusInRange(eq(OrderStatus.DELIVERED), fromCaptor.capture(), toCaptor.capture());

        List<LocalDateTime> froms = fromCaptor.getAllValues();
        List<LocalDateTime> tos = toCaptor.getAllValues();
        assertThat(froms.get(0)).isEqualTo(LocalDateTime.of(2026, 8, 22, 0, 0));
        assertThat(tos.get(0)).isEqualTo(LocalDateTime.of(2026, 8, 29, 0, 0));
        assertThat(froms.get(1)).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
        assertThat(tos.get(1)).isEqualTo(LocalDateTime.of(2026, 8, 22, 0, 0));
    }

    @Test
    void getOrdersByCity_bucketsTopFiveCities_andCollapsesTheRestIntoOther() {
        when(orderRepository.countGroupByCityInRange(any(), any())).thenReturn(List.of(
                new CityOrderCount("Cairo", 50L),
                new CityOrderCount("Giza", 20L),
                new CityOrderCount("Alexandria", 10L),
                new CityOrderCount("Aswan", 8L),
                new CityOrderCount("Luxor", 7L),
                new CityOrderCount("Sohag", 5L)));

        AdminOrdersByCityResponse response = adminAnalyticsService.getOrdersByCity(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        assertThat(response.getTotalOrders()).isEqualTo(100L);
        assertThat(response.getCities()).hasSize(6);
        assertThat(response.getCities()).extracting(CityOrderShareResponse::getCity)
                .containsExactly("Cairo", "Giza", "Alexandria", "Aswan", "Luxor", "Other");
        assertThat(response.getCities()).extracting(CityOrderShareResponse::getOrderCount)
                .containsExactly(50L, 20L, 10L, 8L, 7L, 5L);
        CityOrderShareResponse cairo = response.getCities().get(0);
        assertThat(cairo.getPercentage()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        CityOrderShareResponse other = response.getCities().get(5);
        assertThat(other.getPercentage()).isEqualByComparingTo(BigDecimal.valueOf(5.00));
    }

    @Test
    void getOrdersByCity_omitsOtherBucket_whenFiveOrFewerCities() {
        when(orderRepository.countGroupByCityInRange(any(), any())).thenReturn(List.of(
                new CityOrderCount("Cairo", 3L), new CityOrderCount("Giza", 1L)));

        AdminOrdersByCityResponse response = adminAnalyticsService.getOrdersByCity(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        assertThat(response.getCities()).extracting(CityOrderShareResponse::getCity).containsExactly("Cairo", "Giza");
    }

    @Test
    void getOrdersByCity_returnsEmptyCities_whenNoOrdersInRange() {
        when(orderRepository.countGroupByCityInRange(any(), any())).thenReturn(List.of());

        AdminOrdersByCityResponse response = adminAnalyticsService.getOrdersByCity(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        assertThat(response.getTotalOrders()).isZero();
        assertThat(response.getCities()).isEmpty();
    }

    private void stubOverviewRepositories(long totalOrders, long previousTotalOrders, BigDecimal revenue,
            long completedOrders, BigDecimal previousRevenue, long previousCompletedOrders,
            long activeRestaurants, long previousActiveRestaurants, long registeredCustomers, long previousRegisteredCustomers) {
        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(totalOrders, previousTotalOrders);
        when(orderRepository.sumRevenueByStatusInRange(eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(revenue, completedOrders), new RevenueAggregate(previousRevenue, previousCompletedOrders));
        when(restaurantRepository.countByApprovalStatusAndCreatedAtLessThan(eq(RestaurantApprovalStatus.APPROVED), any()))
                .thenReturn(activeRestaurants, previousActiveRestaurants);
        when(userRepository.countByRoleAndCreatedAtLessThan(eq(Role.CUSTOMER), any()))
                .thenReturn(registeredCustomers, previousRegisteredCustomers);
    }
}
