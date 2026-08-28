package com.food.foodapp.restaurant.service;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.dto.RestaurantDetailResponse;
import com.food.foodapp.restaurant.dto.RestaurantListResponse;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository);
    }

    @Test
    void getVisibleRestaurantById_returnsDetail_whenApprovedAndOpen() {
        Restaurant restaurant = approvedOpenRestaurant();
        when(restaurantRepository.findByIdWithCategories(1L)).thenReturn(Optional.of(restaurant));

        RestaurantDetailResponse response = restaurantService.getVisibleRestaurantById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.isOpenForOrders()).isTrue();
    }

    @Test
    void getVisibleRestaurantById_throwsNotFound_whenNotApproved() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        when(restaurantRepository.findByIdWithCategories(1L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.getVisibleRestaurantById(1L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getVisibleRestaurantById_throwsNotFound_whenClosedForOrders() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setOpenForOrders(false);
        when(restaurantRepository.findByIdWithCategories(1L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.getVisibleRestaurantById(1L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void getVisibleRestaurantById_throwsNotFound_whenMissing() {
        when(restaurantRepository.findByIdWithCategories(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getVisibleRestaurantById(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void searchRestaurants_rejectsInvalidSortValue() {
        assertThatThrownBy(() -> restaurantService.searchRestaurants(null, null, "banana", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void searchRestaurants_rejectsNegativePage() {
        assertThatThrownBy(() -> restaurantService.searchRestaurants(null, null, null, -1, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void searchRestaurants_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> restaurantService.searchRestaurants(null, null, null, 0, 0))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> restaurantService.searchRestaurants(null, null, null, 0, 51))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void searchRestaurants_defaultsToDeterministicIdSort_whenNoSortGiven() {
        stubEmptyPage();

        restaurantService.searchRestaurants(null, null, null, 0, 20);

        assertThat(capturePageable().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void searchRestaurants_sortsByRatingDescending_withIdTiebreaker() {
        stubEmptyPage();

        restaurantService.searchRestaurants(null, null, "rating", 0, 20);

        assertThat(capturePageable().getSort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "ratingAverage").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    void searchRestaurants_sortsByDeliveryTimeAscending_withIdTiebreaker() {
        stubEmptyPage();

        restaurantService.searchRestaurants(null, null, "delivery_time", 0, 20);

        assertThat(capturePageable().getSort()).isEqualTo(
                Sort.by(Sort.Direction.ASC, "estimatedDeliveryMinMinutes").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    void searchRestaurants_sortsByDeliveryFeeAscending_withIdTiebreaker() {
        stubEmptyPage();

        restaurantService.searchRestaurants(null, null, "delivery_fee", 0, 20);

        assertThat(capturePageable().getSort()).isEqualTo(
                Sort.by(Sort.Direction.ASC, "deliveryFee").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    void searchRestaurants_returnsPaginationMetadata() {
        Restaurant restaurant = approvedOpenRestaurant();
        Pageable pageable = Pageable.ofSize(20);
        when(restaurantRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(restaurant), pageable, 1));

        RestaurantListResponse response = restaurantService.searchRestaurants(null, null, null, 0, 20);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getRestaurants()).hasSize(1);
    }

    private void stubEmptyPage() {
        when(restaurantRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(restaurantRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    private Restaurant approvedOpenRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setCuisine("إيطالي");
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(50));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setOpenForOrders(true);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        return restaurant;
    }
}
