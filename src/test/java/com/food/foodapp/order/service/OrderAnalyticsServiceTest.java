package com.food.foodapp.order.service;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.order.dto.OwnerAnalyticsOverviewResponse;
import com.food.foodapp.order.dto.OwnerRevenueAnalyticsResponse;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.order.repository.OrderStatusCount;
import com.food.foodapp.order.repository.RevenueAggregate;
import com.food.foodapp.order.repository.RevenueLine;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantOwnershipGuard;
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
class OrderAnalyticsServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantOwnershipGuard ownershipGuard;

    private OrderAnalyticsService orderAnalyticsService;

    @BeforeEach
    void setUp() {
        orderAnalyticsService = new OrderAnalyticsService(orderRepository, ownershipGuard);
    }

    @Test
    void getOverview_returnsCurrentMonthTotals_withTrendPercentages() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());
        when(orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(5L), any(), any()))
                .thenReturn(20L, 15L);
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.valueOf(500), 10L),
                        new RevenueAggregate(BigDecimal.valueOf(400), 8L));
        when(orderRepository.countByRestaurantIdGroupByStatusInRange(eq(5L), any(), any()))
                .thenReturn(List.of(new OrderStatusCount(OrderStatus.DELIVERED, 10L), new OrderStatusCount(OrderStatus.NEW, 3L)));

        OwnerAnalyticsOverviewResponse response = orderAnalyticsService.getOverview(5L);

        assertThat(response.getRestaurantId()).isEqualTo(5L);
        assertThat(response.getRestaurantName()).isEqualTo("Pizza Place");
        assertThat(response.getPeriodStart()).isEqualTo(LocalDate.now().withDayOfMonth(1));
        assertThat(response.getPeriodEnd()).isEqualTo(LocalDate.now());
        assertThat(response.getTotalOrders()).isEqualTo(20L);
        assertThat(response.getTotalOrdersTrendPercentage()).isEqualByComparingTo(BigDecimal.valueOf(33.33));
        assertThat(response.getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(response.getRevenueTrendPercentage()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        assertThat(response.getCompletedOrders()).isEqualTo(10L);
        assertThat(response.getAverageOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(response.getOrdersByStatus()).hasSize(OrderStatus.values().length);
        assertThat(response.getOrdersByStatus()).filteredOn(row -> row.getStatus() == OrderStatus.NEW)
                .extracting("count").containsExactly(3L);
        assertThat(response.getOrdersByStatus()).filteredOn(row -> row.getStatus() == OrderStatus.CANCELLED)
                .extracting("count").containsExactly(0L);
    }

    @Test
    void getOverview_returnsNullTrend_whenPreviousPeriodHadNoActivity() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());
        when(orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(5L), any(), any()))
                .thenReturn(5L, 0L);
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.valueOf(100), 2L), new RevenueAggregate(null, 0L));
        when(orderRepository.countByRestaurantIdGroupByStatusInRange(eq(5L), any(), any())).thenReturn(List.of());

        OwnerAnalyticsOverviewResponse response = orderAnalyticsService.getOverview(5L);

        assertThat(response.getTotalOrdersTrendPercentage()).isNull();
        assertThat(response.getRevenueTrendPercentage()).isNull();
    }

    @Test
    void getOverview_returnsZeroTrendAndZeroAverage_whenCurrentAndPreviousAreBothEmpty() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());
        when(orderRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(5L), any(), any()))
                .thenReturn(0L, 0L);
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(null, 0L), new RevenueAggregate(null, 0L));
        when(orderRepository.countByRestaurantIdGroupByStatusInRange(eq(5L), any(), any())).thenReturn(List.of());

        OwnerAnalyticsOverviewResponse response = orderAnalyticsService.getOverview(5L);

        assertThat(response.getTotalOrdersTrendPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getRevenueTrendPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getAverageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCompletedOrders()).isZero();
    }

    @Test
    void getOverview_throwsNotFound_whenRestaurantDoesNotExist() {
        when(ownershipGuard.requireOwnedRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> orderAnalyticsService.getOverview(99L)).isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getRevenue_returnsDailyRevenue_zeroFilledForDaysWithNoDeliveredOrders() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 3);
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.valueOf(150), 2L),
                        new RevenueAggregate(BigDecimal.valueOf(100), 1L));
        when(orderRepository.findRevenueLinesByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(List.of(
                        new RevenueLine(LocalDateTime.of(2026, 8, 1, 12, 0), BigDecimal.valueOf(100)),
                        new RevenueLine(LocalDateTime.of(2026, 8, 1, 18, 0), BigDecimal.valueOf(50))));

        OwnerRevenueAnalyticsResponse response = orderAnalyticsService.getRevenue(5L, from, to);

        assertThat(response.getFrom()).isEqualTo(from);
        assertThat(response.getTo()).isEqualTo(to);
        assertThat(response.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.getPreviousPeriodRevenue()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.getChangePercentage()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(response.getDailyRevenue()).hasSize(3);
        assertThat(response.getDailyRevenue().get(0).getDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.getDailyRevenue().get(0).getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.getDailyRevenue().get(0).getOrderCount()).isEqualTo(2);
        assertThat(response.getDailyRevenue().get(1).getRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getDailyRevenue().get(1).getOrderCount()).isZero();
        assertThat(response.getDailyRevenue().get(2).getRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getRevenue_queriesPreviousPeriod_asImmediatelyPrecedingRangeOfSameLength() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.ZERO, 0L));
        when(orderRepository.findRevenueLinesByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(List.of());

        orderAnalyticsService.getRevenue(5L, LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 28));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, times(2)).sumRevenueByRestaurantAndStatusInRange(
                eq(5L), eq(OrderStatus.DELIVERED), fromCaptor.capture(), toCaptor.capture());

        List<LocalDateTime> froms = fromCaptor.getAllValues();
        List<LocalDateTime> tos = toCaptor.getAllValues();
        assertThat(froms.get(0)).isEqualTo(LocalDateTime.of(2026, 8, 22, 0, 0));
        assertThat(tos.get(0)).isEqualTo(LocalDateTime.of(2026, 8, 29, 0, 0));
        assertThat(froms.get(1)).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
        assertThat(tos.get(1)).isEqualTo(LocalDateTime.of(2026, 8, 22, 0, 0));
    }

    @Test
    void getRevenue_defaultsToCurrentSaturdayToFridayWeek_whenBothBoundsOmitted() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());
        when(orderRepository.sumRevenueByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(new RevenueAggregate(BigDecimal.ZERO, 0L));
        when(orderRepository.findRevenueLinesByRestaurantAndStatusInRange(eq(5L), eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(List.of());

        OwnerRevenueAnalyticsResponse response = orderAnalyticsService.getRevenue(5L, null, null);

        assertThat(response.getFrom().getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(response.getTo().getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(ChronoUnit.DAYS.between(response.getFrom(), response.getTo())).isEqualTo(6);
        LocalDate today = LocalDate.now();
        assertThat(response.getFrom()).isBeforeOrEqualTo(today);
        assertThat(response.getTo()).isAfterOrEqualTo(today);
        assertThat(response.getDailyRevenue()).hasSize(7);
    }

    @Test
    void getRevenue_rejectsFromWithoutTo() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());

        assertThatThrownBy(() -> orderAnalyticsService.getRevenue(5L, LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getRevenue_rejectsToWithoutFrom() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());

        assertThatThrownBy(() -> orderAnalyticsService.getRevenue(5L, null, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getRevenue_rejectsFromAfterTo() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());

        assertThatThrownBy(() -> orderAnalyticsService.getRevenue(5L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getRevenue_rejectsRangeExceedingMaxDays() {
        when(ownershipGuard.requireOwnedRestaurant(5L)).thenReturn(restaurant());

        assertThatThrownBy(() -> orderAnalyticsService.getRevenue(5L, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getRevenue_throwsNotFound_whenRestaurantDoesNotExist() {
        when(ownershipGuard.requireOwnedRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> orderAnalyticsService.getRevenue(99L, null, null))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setName("Pizza Place");
        return restaurant;
    }
}
