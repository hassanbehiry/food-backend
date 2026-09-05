package com.food.foodapp.menu.service;

import com.food.foodapp.common.exception.DuplicateMenuCategoryException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.MenuCategoryNotFoundException;
import com.food.foodapp.menu.dto.MenuCategoryCreateRequest;
import com.food.foodapp.menu.dto.MenuCategoryReorderRequest;
import com.food.foodapp.menu.dto.MenuCategoryResponse;
import com.food.foodapp.menu.dto.MenuCategoryUpdateRequest;
import com.food.foodapp.menu.entity.MenuCategory;
import com.food.foodapp.menu.mapper.MenuCategoryMapper;
import com.food.foodapp.menu.repository.MenuCategoryRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantOwnershipGuard;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-restaurant menu category (tab) management. Distinct from
 * {@link com.food.foodapp.category.service.CategoryService}, which manages the
 * platform-wide discovery taxonomy.
 */
@Service
@RequiredArgsConstructor
public class MenuCategoryService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final RestaurantService restaurantService;
    private final RestaurantOwnershipGuard ownershipGuard;

    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> listVisibleCategories(Long restaurantId) {
        restaurantService.requireApprovedRestaurant(restaurantId);
        return menuCategoryRepository.findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(restaurantId).stream()
                .map(MenuCategoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> listCategoriesForOwner(Long restaurantId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        return menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurantId).stream()
                .map(MenuCategoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public MenuCategoryResponse createCategory(Long restaurantId, MenuCategoryCreateRequest request) {
        Restaurant restaurant = ownershipGuard.requireOwnedRestaurant(restaurantId);
        String name = request.getName().trim();
        requireUniqueName(restaurantId, name, null);

        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName(name);
        category.setDisplayOrder(menuCategoryRepository.findMaxDisplayOrder(restaurantId) + 1);

        return MenuCategoryMapper.toResponse(menuCategoryRepository.save(category));
    }

    @Transactional
    public MenuCategoryResponse updateCategory(Long restaurantId, Long categoryId, MenuCategoryUpdateRequest request) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        MenuCategory category = requireOwnedCategory(restaurantId, categoryId);
        String name = request.getName().trim();
        requireUniqueName(restaurantId, name, categoryId);

        category.setName(name);
        category.setActive(request.getActive());

        return MenuCategoryMapper.toResponse(menuCategoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long restaurantId, Long categoryId) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        MenuCategory category = requireOwnedCategory(restaurantId, categoryId);
        menuCategoryRepository.delete(category);
    }

    @Transactional
    public List<MenuCategoryResponse> reorderCategories(Long restaurantId, MenuCategoryReorderRequest request) {
        ownershipGuard.requireOwnedRestaurant(restaurantId);
        List<MenuCategory> existing = menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurantId);

        List<Long> requestedIds = request.getCategoryIds();
        Set<Long> existingIds = existing.stream().map(MenuCategory::getId).collect(Collectors.toSet());
        boolean sameSize = requestedIds.size() == existingIds.size();
        boolean noDuplicates = new HashSet<>(requestedIds).size() == requestedIds.size();
        if (!sameSize || !noDuplicates || !existingIds.containsAll(requestedIds)) {
            throw new InvalidRequestParameterException(
                    "categoryIds must contain exactly the current set of category ids for restaurant "
                            + restaurantId + ", without duplicates");
        }

        Map<Long, MenuCategory> byId = existing.stream()
                .collect(Collectors.toMap(MenuCategory::getId, Function.identity()));
        for (int position = 0; position < requestedIds.size(); position++) {
            byId.get(requestedIds.get(position)).setDisplayOrder(position);
        }

        return existing.stream()
                .sorted(Comparator.comparingInt(MenuCategory::getDisplayOrder))
                .map(MenuCategoryMapper::toResponse)
                .toList();
    }

    private MenuCategory requireOwnedCategory(Long restaurantId, Long categoryId) {
        return menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new MenuCategoryNotFoundException(
                        "Menu category " + categoryId + " not found for restaurant " + restaurantId));
    }

    private void requireUniqueName(Long restaurantId, String name, Long excludeCategoryId) {
        boolean duplicate = excludeCategoryId == null
                ? menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)
                : menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(restaurantId, name, excludeCategoryId);
        if (duplicate) {
            throw new DuplicateMenuCategoryException(
                    "Menu category '" + name + "' already exists for restaurant " + restaurantId);
        }
    }
}
