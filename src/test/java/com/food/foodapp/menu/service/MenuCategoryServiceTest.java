package com.food.foodapp.menu.service;

import com.food.foodapp.common.exception.DuplicateMenuCategoryException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.dto.MenuCategoryCreateRequest;
import com.food.foodapp.menu.dto.MenuCategoryReorderRequest;
import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.dto.MenuCategoryUpdateRequest;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.repository.MenuCategoryRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
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
class MenuCategoryServiceTest {

    @Mock
    private MenuCategoryRepository menuCategoryRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    private MenuCategoryService menuCategoryService;

    @BeforeEach
    void setUp() {
        RestaurantService restaurantService = new RestaurantService(restaurantRepository);
        menuCategoryService = new MenuCategoryService(menuCategoryRepository, restaurantService);
    }

    @Test
    void listVisibleCategories_returnsOnlyActiveCategoriesInOrder_whenRestaurantVisible() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        when(menuCategoryRepository.findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(category(10L, "بيتزا", 0, true)));

        List<MenuCategoryResponse> result = menuCategoryService.listVisibleCategories(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("بيتزا");
    }

    @Test
    void listVisibleCategories_throwsNotFound_whenRestaurantNotVisible() {
        Restaurant pending = approvedOpenRestaurant();
        pending.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> menuCategoryService.listVisibleCategories(1L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void listCategoriesForOwner_includesInactiveCategories() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        when(menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(category(10L, "بيتزا", 0, true), category(11L, "مشروبات", 1, false)));

        List<MenuCategoryResponse> result = menuCategoryService.listCategoriesForOwner(1L);

        assertThat(result).extracting(MenuCategoryResponse::getName).containsExactly("بيتزا", "مشروبات");
    }

    @Test
    void createCategory_appendsToEndOfDisplayOrder() {
        Restaurant restaurant = approvedOpenRestaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(1L, "باستا")).thenReturn(false);
        when(menuCategoryRepository.findMaxDisplayOrder(1L)).thenReturn(2);
        when(menuCategoryRepository.save(any(MenuCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuCategoryCreateRequest request = new MenuCategoryCreateRequest();
        request.setName("باستا");

        MenuCategoryResponse response = menuCategoryService.createCategory(1L, request);

        assertThat(response.getDisplayOrder()).isEqualTo(3);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createCategory_throwsNotFound_whenRestaurantMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());
        MenuCategoryCreateRequest request = new MenuCategoryCreateRequest();
        request.setName("باستا");

        assertThatThrownBy(() -> menuCategoryService.createCategory(99L, request))
                .isInstanceOf(RestaurantNotFoundException.class);
        verify(menuCategoryRepository, never()).save(any());
    }

    @Test
    void createCategory_throwsDuplicate_whenNameAlreadyExistsForRestaurant() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        when(menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(1L, "بيتزا")).thenReturn(true);
        MenuCategoryCreateRequest request = new MenuCategoryCreateRequest();
        request.setName("بيتزا");

        assertThatThrownBy(() -> menuCategoryService.createCategory(1L, request))
                .isInstanceOf(DuplicateMenuCategoryException.class);
        verify(menuCategoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_updatesNameAndActive_whenCategoryBelongsToRestaurant() {
        MenuCategory existing = category(10L, "بيتزا", 0, true);
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(existing));
        when(menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(1L, "بيتزا مميزة", 10L))
                .thenReturn(false);
        when(menuCategoryRepository.save(any(MenuCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuCategoryUpdateRequest request = new MenuCategoryUpdateRequest();
        request.setName("بيتزا مميزة");
        request.setActive(false);

        MenuCategoryResponse response = menuCategoryService.updateCategory(1L, 10L, request);

        assertThat(response.getName()).isEqualTo("بيتزا مميزة");
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void updateCategory_throwsNotFound_whenCategoryBelongsToAnotherRestaurant() {
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 2L)).thenReturn(Optional.empty());
        MenuCategoryUpdateRequest request = new MenuCategoryUpdateRequest();
        request.setName("بيتزا");
        request.setActive(true);

        assertThatThrownBy(() -> menuCategoryService.updateCategory(2L, 10L, request))
                .isInstanceOf(MenuCategoryNotFoundException.class);
    }

    @Test
    void deleteCategory_deletes_whenCategoryBelongsToRestaurant() {
        MenuCategory existing = category(10L, "بيتزا", 0, true);
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(existing));

        menuCategoryService.deleteCategory(1L, 10L);

        verify(menuCategoryRepository).delete(existing);
    }

    @Test
    void deleteCategory_throwsNotFound_whenCategoryBelongsToAnotherRestaurant() {
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuCategoryService.deleteCategory(2L, 10L))
                .isInstanceOf(MenuCategoryNotFoundException.class);
    }

    @Test
    void reorderCategories_reassignsDisplayOrderByRequestedPosition() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        MenuCategory first = category(10L, "بيتزا", 0, true);
        MenuCategory second = category(11L, "مشروبات", 1, true);
        when(menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(first, second));

        MenuCategoryReorderRequest request = new MenuCategoryReorderRequest();
        request.setCategoryIds(List.of(11L, 10L));

        List<MenuCategoryResponse> result = menuCategoryService.reorderCategories(1L, request);

        assertThat(result).extracting(MenuCategoryResponse::getId).containsExactly(11L, 10L);
        assertThat(second.getDisplayOrder()).isEqualTo(0);
        assertThat(first.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void reorderCategories_rejectsRequest_whenIdSetDoesNotMatchExisting() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        when(menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(category(10L, "بيتزا", 0, true)));

        MenuCategoryReorderRequest request = new MenuCategoryReorderRequest();
        request.setCategoryIds(List.of(10L, 999L));

        assertThatThrownBy(() -> menuCategoryService.reorderCategories(1L, request))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void reorderCategories_rejectsRequest_whenDuplicateIdsGiven() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        when(menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(category(10L, "بيتزا", 0, true), category(11L, "مشروبات", 1, true)));

        MenuCategoryReorderRequest request = new MenuCategoryReorderRequest();
        request.setCategoryIds(List.of(10L, 10L));

        assertThatThrownBy(() -> menuCategoryService.reorderCategories(1L, request))
                .isInstanceOf(InvalidRequestParameterException.class);
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

    private MenuCategory category(Long id, String name, int displayOrder, boolean active) {
        MenuCategory category = new MenuCategory();
        category.setId(id);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setActive(active);
        return category;
    }
}
