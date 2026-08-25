package com.food.foodapp.menu.service;

import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.common.exception.MenuItemNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.menu.dto.MenuItemAvailabilityRequest;
import com.food.foodapp.menu.dto.MenuItemCreateRequest;
import com.food.foodapp.menu.dto.MenuItemResponse;
import com.food.foodapp.menu.dto.MenuItemUpdateRequest;
import com.food.foodapp.menu.dto.OwnerMenuItemResponse;
import com.food.foodapp.menu.dto.RestaurantMenuResponse;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.menu.repository.MenuCategoryRepository;
import com.food.foodapp.menu.repository.MenuItemRepository;
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
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private MenuCategoryRepository menuCategoryRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    private MenuItemService menuItemService;

    @BeforeEach
    void setUp() {
        RestaurantService restaurantService = new RestaurantService(restaurantRepository);
        MenuCategoryService menuCategoryService = new MenuCategoryService(menuCategoryRepository, restaurantService);
        menuItemService = new MenuItemService(menuItemRepository, menuCategoryRepository, menuCategoryService, restaurantService);
    }

    @Test
    void getMenu_returnsTabsAndItems_whenRestaurantVisible() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        when(menuCategoryRepository.findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(pizza));
        when(menuItemRepository.findVisibleByRestaurantId(1L))
                .thenReturn(List.of(item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 0)));

        RestaurantMenuResponse response = menuItemService.getMenu(1L);

        assertThat(response.getTabs()).containsExactly("بيتزا");
        assertThat(response.getItems()).extracting(MenuItemResponse::getTab).containsExactly("بيتزا");
        assertThat(response.getItems()).extracting(MenuItemResponse::getName).containsExactly("مارجريتا");
    }

    @Test
    void getMenu_throwsNotFound_whenRestaurantNotVisible() {
        Restaurant pending = approvedOpenRestaurant();
        pending.setApprovalStatus(RestaurantApprovalStatus.PENDING);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> menuItemService.getMenu(1L)).isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void listVisibleItems_returnsAllVisibleItems_whenNoCategoryGiven() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        when(menuItemRepository.findVisibleByRestaurantId(1L))
                .thenReturn(List.of(item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 0)));

        List<MenuItemResponse> result = menuItemService.listVisibleItems(1L, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void listVisibleItems_filtersByCategory_whenCategoryGiven() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(pizza));
        when(menuItemRepository.findByCategoryIdOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 0)));

        List<MenuItemResponse> result = menuItemService.listVisibleItems(1L, 10L);

        assertThat(result).extracting(MenuItemResponse::getTab).containsExactly("بيتزا");
    }

    @Test
    void listVisibleItems_throwsNotFound_whenCategoryInactive() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        MenuCategory inactive = category(10L, "قديم", 0, false);
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> menuItemService.listVisibleItems(1L, 10L))
                .isInstanceOf(MenuCategoryNotFoundException.class);
    }

    @Test
    void listItemsForOwner_includesItemsFromInactiveCategories() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        MenuCategory archived = category(10L, "قديم", 0, false);
        when(menuItemRepository.findAllByRestaurantIdOrdered(1L))
                .thenReturn(List.of(item(100L, archived, "منتج قديم", BigDecimal.valueOf(50), true, 0)));

        List<OwnerMenuItemResponse> result = menuItemService.listItemsForOwner(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void createItem_appendsToEndOfCategoryDisplayOrder() {
        Restaurant restaurant = approvedOpenRestaurant();
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(pizza));
        when(menuItemRepository.findMaxDisplayOrderInCategory(10L)).thenReturn(2);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemCreateRequest request = new MenuItemCreateRequest();
        request.setCategoryId(10L);
        request.setName("مارجريتا");
        request.setPrice(BigDecimal.valueOf(50));

        OwnerMenuItemResponse response = menuItemService.createItem(1L, request);

        assertThat(response.getDisplayOrder()).isEqualTo(3);
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getCategoryId()).isEqualTo(10L);
    }

    @Test
    void createItem_throwsNotFound_whenCategoryBelongsToAnotherRestaurant() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(approvedOpenRestaurant()));
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.empty());

        MenuItemCreateRequest request = new MenuItemCreateRequest();
        request.setCategoryId(10L);
        request.setName("مارجريتا");
        request.setPrice(BigDecimal.valueOf(50));

        assertThatThrownBy(() -> menuItemService.createItem(1L, request))
                .isInstanceOf(MenuCategoryNotFoundException.class);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void createItem_throwsNotFound_whenRestaurantMissing() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());
        MenuItemCreateRequest request = new MenuItemCreateRequest();
        request.setCategoryId(10L);
        request.setName("مارجريتا");
        request.setPrice(BigDecimal.valueOf(50));

        assertThatThrownBy(() -> menuItemService.createItem(99L, request))
                .isInstanceOf(RestaurantNotFoundException.class);
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void updateItem_keepsDisplayOrder_whenCategoryUnchanged() {
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        MenuItem existing = item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 5);
        when(menuItemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(existing));
        when(menuCategoryRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(pizza));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemUpdateRequest request = new MenuItemUpdateRequest();
        request.setCategoryId(10L);
        request.setName("مارجريتا سبيشل");
        request.setPrice(BigDecimal.valueOf(60));

        OwnerMenuItemResponse response = menuItemService.updateItem(1L, 100L, request);

        assertThat(response.getDisplayOrder()).isEqualTo(5);
        assertThat(response.getName()).isEqualTo("مارجريتا سبيشل");
        assertThat(response.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(60));
        verify(menuItemRepository, never()).findMaxDisplayOrderInCategory(any());
    }

    @Test
    void updateItem_reassignsDisplayOrder_whenCategoryChanged() {
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        MenuCategory drinks = category(11L, "مشروبات", 1, true);
        MenuItem existing = item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 5);
        when(menuItemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(existing));
        when(menuCategoryRepository.findByIdAndRestaurantId(11L, 1L)).thenReturn(Optional.of(drinks));
        when(menuItemRepository.findMaxDisplayOrderInCategory(11L)).thenReturn(1);
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemUpdateRequest request = new MenuItemUpdateRequest();
        request.setCategoryId(11L);
        request.setName("مارجريتا");
        request.setPrice(BigDecimal.valueOf(50));

        OwnerMenuItemResponse response = menuItemService.updateItem(1L, 100L, request);

        assertThat(response.getDisplayOrder()).isEqualTo(2);
        assertThat(response.getCategoryId()).isEqualTo(11L);
    }

    @Test
    void updateItem_throwsNotFound_whenItemBelongsToAnotherRestaurant() {
        when(menuItemRepository.findByIdAndRestaurantId(100L, 2L)).thenReturn(Optional.empty());
        MenuItemUpdateRequest request = new MenuItemUpdateRequest();
        request.setCategoryId(10L);
        request.setName("مارجريتا");
        request.setPrice(BigDecimal.valueOf(50));

        assertThatThrownBy(() -> menuItemService.updateItem(2L, 100L, request))
                .isInstanceOf(MenuItemNotFoundException.class);
    }

    @Test
    void deleteItem_deletes_whenItemBelongsToRestaurant() {
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        MenuItem existing = item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 0);
        when(menuItemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(existing));

        menuItemService.deleteItem(1L, 100L);

        verify(menuItemRepository).delete(existing);
    }

    @Test
    void deleteItem_throwsNotFound_whenItemBelongsToAnotherRestaurant() {
        when(menuItemRepository.findByIdAndRestaurantId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.deleteItem(2L, 100L))
                .isInstanceOf(MenuItemNotFoundException.class);
    }

    @Test
    void updateAvailability_updatesFlagOnly() {
        MenuCategory pizza = category(10L, "بيتزا", 0, true);
        MenuItem existing = item(100L, pizza, "مارجريتا", BigDecimal.valueOf(50), true, 0);
        when(menuItemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(existing));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemAvailabilityRequest request = new MenuItemAvailabilityRequest();
        request.setAvailable(false);

        OwnerMenuItemResponse response = menuItemService.updateAvailability(1L, 100L, request);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getName()).isEqualTo("مارجريتا");
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

    private MenuItem item(Long id, MenuCategory category, String name, BigDecimal price, boolean available, int displayOrder) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setCategory(category);
        item.setName(name);
        item.setPrice(price);
        item.setAvailable(available);
        item.setDisplayOrder(displayOrder);
        return item;
    }
}
