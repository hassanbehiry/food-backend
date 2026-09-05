package com.food.foodapp.menu.service;

import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.common.exception.MenuItemNotFoundException;
import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.dto.MenuItemAvailabilityRequest;
import com.food.foodapp.menu.dto.MenuItemCreateRequest;
import com.food.foodapp.menu.dto.MenuItemResponse;
import com.food.foodapp.menu.dto.MenuItemUpdateRequest;
import com.food.foodapp.menu.dto.OwnerMenuItemResponse;
import com.food.foodapp.menu.dto.RestaurantMenuResponse;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.menu.mapper.MenuItemMapper;
import com.food.foodapp.menu.repository.MenuCategoryRepository;
import com.food.foodapp.menu.repository.MenuItemRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantOwnershipGuard;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Per-restaurant menu item management and customer menu retrieval. Items always
 * carry both a {@code restaurant} and a {@code category} reference so ownership can
 * be validated directly — a category id is only ever accepted after confirming it
 * belongs to the same {@code restaurantId} as the item.
 */
@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuCategoryService menuCategoryService;
    private final RestaurantService restaurantService;
    private final RestaurantOwnershipGuard ownershipGuard;

    @Transactional(readOnly = true)
    public RestaurantMenuResponse getMenu(Long restaurantId) {
        List<String> tabs = menuCategoryService.listVisibleCategories(restaurantId).stream()
                .map(MenuCategoryResponse::getName)
                .toList();
        List<MenuItemResponse> items = menuItemRepository.findVisibleByRestaurantId(restaurantId).stream()
                .map(item -> MenuItemMapper.toResponse(item, item.getCategory().getName()))
                .toList();
        return RestaurantMenuResponse.builder().tabs(tabs).items(items).build();
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> listVisibleItems(Long restaurantId, Long categoryId) {
        restaurantService.requireVisibleRestaurant(restaurantId);

        if (categoryId == null) {
            return menuItemRepository.findVisibleByRestaurantId(restaurantId).stream()
                    .map(item -> MenuItemMapper.toResponse(item, item.getCategory().getName()))
                    .toList();
        }

        MenuCategory category = menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .filter(MenuCategory::isActive)
                .orElseThrow(() -> new MenuCategoryNotFoundException(
                        "Menu category " + categoryId + " not found for restaurant " + restaurantId));

        return menuItemRepository.findByCategoryIdOrderByDisplayOrderAscIdAsc(category.getId()).stream()
                .map(item -> MenuItemMapper.toResponse(item, category.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerMenuItemResponse> listItemsForOwner(Long restaurantId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        return menuItemRepository.findAllByRestaurantIdOrdered(restaurantId).stream()
                .map(MenuItemMapper::toOwnerResponse)
                .toList();
    }

    @Transactional
    public OwnerMenuItemResponse createItem(Long restaurantId, MenuItemCreateRequest request) {
        Restaurant restaurant = ownershipGuard.requireOwnedRestaurant(restaurantId);
        MenuCategory category = requireOwnedCategory(restaurantId, request.getCategoryId());

        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName(request.getName().trim());
        item.setDescription(request.getDesc());
        item.setPrice(request.getPrice());
        item.setImageUrl(request.getImg());
        item.setDisplayOrder(menuItemRepository.findMaxDisplayOrderInCategory(category.getId()) + 1);

        return MenuItemMapper.toOwnerResponse(menuItemRepository.save(item));
    }

    @Transactional
    public OwnerMenuItemResponse updateItem(Long restaurantId, Long itemId, MenuItemUpdateRequest request) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        MenuItem item = requireOwnedItem(restaurantId, itemId);
        MenuCategory category = requireOwnedCategory(restaurantId, request.getCategoryId());

        if (!item.getCategory().getId().equals(category.getId())) {
            item.setDisplayOrder(menuItemRepository.findMaxDisplayOrderInCategory(category.getId()) + 1);
        }
        item.setCategory(category);
        item.setName(request.getName().trim());
        item.setDescription(request.getDesc());
        item.setPrice(request.getPrice());
        item.setImageUrl(request.getImg());

        return MenuItemMapper.toOwnerResponse(menuItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long restaurantId, Long itemId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        MenuItem item = requireOwnedItem(restaurantId, itemId);
        menuItemRepository.delete(item);
    }

    @Transactional
    public OwnerMenuItemResponse updateAvailability(Long restaurantId, Long itemId, MenuItemAvailabilityRequest request) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        MenuItem item = requireOwnedItem(restaurantId, itemId);
        item.setAvailable(request.getAvailable());
        return MenuItemMapper.toOwnerResponse(menuItemRepository.save(item));
    }

    private MenuItem requireOwnedItem(Long restaurantId, Long itemId) {
        return menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new MenuItemNotFoundException(
                        "Menu item " + itemId + " not found for restaurant " + restaurantId));
    }

    private MenuCategory requireOwnedCategory(Long restaurantId, Long categoryId) {
        return menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new MenuCategoryNotFoundException(
                        "Menu category " + categoryId + " not found for restaurant " + restaurantId));
    }
}
