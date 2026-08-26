package com.food.foodapp.favorite.service;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.common.exception.UnauthenticatedException;
import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.dto.FavoriteToggleRequest;
import com.food.foodapp.favorite.dto.FavoriteToggleResponse;
import com.food.foodapp.favorite.entity.Favorite;
import com.food.foodapp.favorite.repository.FavoriteRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private UserContext userContext;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository, userRepository, restaurantService, userContext);
    }

    @Test
    void listFavorites_returnsMappedFavorites_withCurrentRestaurantData() {
        when(userContext.getCurrentUserId()).thenReturn(1L);
        Restaurant restaurant = restaurant(10L, true, true);
        Favorite favorite = favorite(100L, restaurant);
        when(favoriteRepository.findByCustomerIdWithRestaurant(1L)).thenReturn(List.of(favorite));

        List<FavoriteResponse> responses = favoriteService.listFavorites();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getRestaurant().getId()).isEqualTo(10L);
        assertThat(responses.get(0).isAvailable()).isTrue();
    }

    @Test
    void listFavorites_flagsUnavailable_whenRestaurantIsSuspended() {
        when(userContext.getCurrentUserId()).thenReturn(1L);
        Restaurant suspended = restaurant(11L, false, true);
        suspended.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        when(favoriteRepository.findByCustomerIdWithRestaurant(1L)).thenReturn(List.of(favorite(101L, suspended)));

        List<FavoriteResponse> responses = favoriteService.listFavorites();

        assertThat(responses.get(0).isAvailable()).isFalse();
    }

    @Test
    void toggleFavorite_createsFavorite_whenNotAlreadyFavorited() {
        stubLock(1L);
        Restaurant restaurant = restaurant(10L, true, true);
        when(restaurantService.requireRestaurant(10L)).thenReturn(restaurant);
        when(favoriteRepository.findByCustomerIdAndRestaurantId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(new User());

        FavoriteToggleResponse response = favoriteService.toggleFavorite(toggleRequest(10L));

        assertThat(response.isFavorite()).isTrue();
        verify(favoriteRepository).save(any(Favorite.class));
        verify(favoriteRepository, never()).delete(any());
    }

    @Test
    void toggleFavorite_removesFavorite_whenAlreadyFavorited() {
        stubLock(1L);
        Restaurant restaurant = restaurant(10L, true, true);
        when(restaurantService.requireRestaurant(10L)).thenReturn(restaurant);
        Favorite existing = favorite(200L, restaurant);
        when(favoriteRepository.findByCustomerIdAndRestaurantId(1L, 10L)).thenReturn(Optional.of(existing));

        FavoriteToggleResponse response = favoriteService.toggleFavorite(toggleRequest(10L));

        assertThat(response.isFavorite()).isFalse();
        verify(favoriteRepository).delete(existing);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void toggleFavorite_allowsFavoritingASuspendedRestaurant() {
        stubLock(1L);
        Restaurant suspended = restaurant(12L, false, true);
        suspended.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        when(restaurantService.requireRestaurant(12L)).thenReturn(suspended);
        when(favoriteRepository.findByCustomerIdAndRestaurantId(1L, 12L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(new User());

        FavoriteToggleResponse response = favoriteService.toggleFavorite(toggleRequest(12L));

        assertThat(response.isFavorite()).isTrue();
    }

    @Test
    void toggleFavorite_throwsNotFound_whenRestaurantDoesNotExist() {
        stubLock(1L);
        when(restaurantService.requireRestaurant(999L)).thenThrow(new RestaurantNotFoundException("Restaurant not found: 999"));

        assertThatThrownBy(() -> favoriteService.toggleFavorite(toggleRequest(999L)))
                .isInstanceOf(RestaurantNotFoundException.class);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void toggleFavorite_throwsUnauthenticated_whenCallerHasNoUserRow() {
        when(userContext.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.toggleFavorite(toggleRequest(10L)))
                .isInstanceOf(UnauthenticatedException.class);
    }

    private void stubLock(Long customerId) {
        when(userContext.getCurrentUserId()).thenReturn(customerId);
        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(new User()));
    }

    private FavoriteToggleRequest toggleRequest(Long restaurantId) {
        FavoriteToggleRequest request = new FavoriteToggleRequest();
        request.setRestaurantId(restaurantId);
        return request;
    }

    private Favorite favorite(Long id, Restaurant restaurant) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setRestaurant(restaurant);
        return favorite;
    }

    private Restaurant restaurant(Long id, boolean openForOrders, boolean approved) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setName("Restaurant " + id);
        restaurant.setCuisine("Italian");
        restaurant.setRatingAverage(BigDecimal.valueOf(4.5));
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(50));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setOpenForOrders(openForOrders);
        restaurant.setApprovalStatus(approved ? RestaurantApprovalStatus.APPROVED : RestaurantApprovalStatus.PENDING);
        return restaurant;
    }
}
