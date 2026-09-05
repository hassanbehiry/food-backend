package com.food.foodapp.restaurant.service;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.InvalidRestaurantApprovalTransitionException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.dto.AdminRestaurantListResponse;
import com.food.foodapp.restaurant.dto.AdminRestaurantResponse;
import com.food.foodapp.restaurant.dto.OwnerRestaurantResponse;
import com.food.foodapp.restaurant.dto.RestaurantAvailabilityRequest;
import com.food.foodapp.restaurant.dto.RestaurantDetailResponse;
import com.food.foodapp.restaurant.dto.RestaurantListResponse;
import com.food.foodapp.restaurant.dto.RestaurantSettingsUpdateRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantOwnershipGuard ownershipGuard;

    private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository, ownershipGuard);
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

    @Test
    void getOwnerRestaurant_returnsResponse_regardlessOfApprovalOrOpenStatus() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        restaurant.setOpenForOrders(false);
        when(ownershipGuard.requireOwnedRestaurant(1L)).thenReturn(restaurant);

        OwnerRestaurantResponse response = restaurantService.getOwnerRestaurant(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.isOpenForOrders()).isFalse();
    }

    @Test
    void getOwnerRestaurant_propagatesNotFound_fromOwnershipGuard() {
        when(ownershipGuard.requireOwnedRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> restaurantService.getOwnerRestaurant(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void updateSettings_updatesFieldsAndReturnsResponse() {
        Restaurant restaurant = approvedOpenRestaurant();
        when(ownershipGuard.requireOwnedRestaurant(1L)).thenReturn(restaurant);
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OwnerRestaurantResponse response = restaurantService.updateSettings(1L, settingsRequest());

        assertThat(response.getName()).isEqualTo("Updated Name");
        assertThat(response.getCuisine()).isEqualTo("مصري");
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(response.getMinimumOrder()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(response.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.getCloseTime()).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    void updateSettings_propagatesNotFound_fromOwnershipGuard() {
        when(ownershipGuard.requireOwnedRestaurant(99L))
                .thenThrow(new RestaurantNotFoundException("Restaurant not found: 99"));

        assertThatThrownBy(() -> restaurantService.updateSettings(99L, settingsRequest()))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void updateSettings_rejectsCloseTimeNotAfterOpenTime() {
        Restaurant restaurant = approvedOpenRestaurant();
        when(ownershipGuard.requireOwnedRestaurant(1L)).thenReturn(restaurant);

        RestaurantSettingsUpdateRequest request = settingsRequest();
        request.setOpenTime(LocalTime.of(12, 0));
        request.setCloseTime(LocalTime.of(12, 0));

        assertThatThrownBy(() -> restaurantService.updateSettings(1L, request))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateAvailability_updatesFlag() {
        Restaurant restaurant = approvedOpenRestaurant();
        when(ownershipGuard.requireOwnedRestaurant(1L)).thenReturn(restaurant);
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantAvailabilityRequest request = new RestaurantAvailabilityRequest();
        request.setOpenForOrders(false);

        OwnerRestaurantResponse response = restaurantService.updateAvailability(1L, request);

        assertThat(response.isOpenForOrders()).isFalse();
    }

    @Test
    void updateAvailability_propagatesForbidden_fromOwnershipGuard() {
        when(ownershipGuard.requireOwnedRestaurant(99L))
                .thenThrow(new com.food.foodapp.common.exception.OwnerAccessDeniedException("nope"));

        RestaurantAvailabilityRequest request = new RestaurantAvailabilityRequest();
        request.setOpenForOrders(false);

        assertThatThrownBy(() -> restaurantService.updateAvailability(99L, request))
                .isInstanceOf(com.food.foodapp.common.exception.OwnerAccessDeniedException.class);
    }

    @Test
    void listRestaurantsForAdmin_appliesApprovalStatusFilter() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        Pageable pageable = Pageable.ofSize(20);
        when(restaurantRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(restaurant), pageable, 1));

        AdminRestaurantListResponse response = restaurantService.listRestaurantsForAdmin("pending", 0, 20);

        assertThat(response.getRestaurants()).hasSize(1);
        assertThat(response.getRestaurants().get(0).getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.PENDING);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listRestaurantsForAdmin_rejectsInvalidStatusValue() {
        assertThatThrownBy(() -> restaurantService.listRestaurantsForAdmin("banana", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listRestaurantsForAdmin_listsEveryStatus_whenNoFilterGiven() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(restaurantRepository.findAll((Specification<Restaurant>) null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        restaurantService.listRestaurantsForAdmin(null, 0, 20);

        verify(restaurantRepository).findAll((Specification<Restaurant>) null, pageable);
    }

    @Test
    void getAdminRestaurant_returnsResponse_regardlessOfApprovalOrOpenStatus() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        AdminRestaurantResponse response = restaurantService.getAdminRestaurant(1L);

        assertThat(response.getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.SUSPENDED);
    }

    @Test
    void getAdminRestaurant_throwsNotFound_whenMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getAdminRestaurant(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void approveRestaurant_movesPendingToApproved() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRestaurantResponse response = restaurantService.approveRestaurant(1L);

        assertThat(response.getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.APPROVED);
    }

    @Test
    void approveRestaurant_restoresASuspendedRestaurant() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRestaurantResponse response = restaurantService.approveRestaurant(1L);

        assertThat(response.getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.APPROVED);
    }

    @Test
    void approveRestaurant_rejectsAlreadyApprovedRestaurant() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.approveRestaurant(1L))
                .isInstanceOf(InvalidRestaurantApprovalTransitionException.class);
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void approveRestaurant_throwsNotFound_whenMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.approveRestaurant(99L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void rejectRestaurant_movesPendingToRejected() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRestaurantResponse response = restaurantService.rejectRestaurant(1L);

        assertThat(response.getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.REJECTED);
    }

    @Test
    void rejectRestaurant_rejectsAlreadyApprovedRestaurant() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.rejectRestaurant(1L))
                .isInstanceOf(InvalidRestaurantApprovalTransitionException.class);
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void suspendRestaurant_movesApprovedToSuspended() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminRestaurantResponse response = restaurantService.suspendRestaurant(1L);

        assertThat(response.getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.SUSPENDED);
    }

    @Test
    void suspendRestaurant_rejectsPendingRestaurant() {
        Restaurant restaurant = approvedOpenRestaurant();
        restaurant.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.suspendRestaurant(1L))
                .isInstanceOf(InvalidRestaurantApprovalTransitionException.class);
        verify(restaurantRepository, never()).save(any());
    }

    private RestaurantSettingsUpdateRequest settingsRequest() {
        RestaurantSettingsUpdateRequest request = new RestaurantSettingsUpdateRequest();
        request.setName("Updated Name");
        request.setCuisine("مصري");
        request.setDeliveryFee(BigDecimal.valueOf(15));
        request.setMinimumOrder(BigDecimal.valueOf(60));
        request.setOpenTime(LocalTime.of(9, 0));
        request.setCloseTime(LocalTime.of(23, 0));
        return request;
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
