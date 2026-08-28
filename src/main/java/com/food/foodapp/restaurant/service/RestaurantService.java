package com.food.foodapp.restaurant.service;

import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.restaurant.dto.RestaurantDetailResponse;
import com.food.foodapp.restaurant.dto.RestaurantListResponse;
import com.food.foodapp.restaurant.dto.RestaurantSortOption;
import com.food.foodapp.restaurant.dto.RestaurantSummaryResponse;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.mapper.RestaurantMapper;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import com.food.foodapp.restaurant.repository.RestaurantSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Restaurant discovery: list/search/filter/sort and detail lookup.
 * Customer-facing results are always restricted to admin-approved, currently-open
 * restaurants — see {@link #isCustomerVisible(Restaurant)}.
 */
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public RestaurantListResponse searchRestaurants(String q, Long categoryId, String sort, int page, int size) {
        validatePagination(page, size);
        RestaurantSortOption sortOption = resolveSortOption(sort);

        Specification<Restaurant> specification = Specification.where(RestaurantSpecifications.isCustomerVisible());
        if (q != null && !q.isBlank()) {
            specification = specification.and(RestaurantSpecifications.nameOrCuisineContains(q.trim()));
        }
        if (categoryId != null) {
            specification = specification.and(RestaurantSpecifications.hasCategoryId(categoryId));
        }

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortOption));
        Page<Restaurant> result = restaurantRepository.findAll(specification, pageable);

        List<RestaurantSummaryResponse> restaurants = result.getContent().stream()
                .map(RestaurantMapper::toSummary)
                .toList();

        return RestaurantListResponse.builder()
                .restaurants(restaurants)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getVisibleRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findByIdWithCategories(id)
                .filter(RestaurantService::isCustomerVisible)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + id));
        return RestaurantMapper.toDetail(restaurant);
    }

    private static boolean isCustomerVisible(Restaurant restaurant) {
        return restaurant.getApprovalStatus() == RestaurantApprovalStatus.APPROVED && restaurant.isOpenForOrders();
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestParameterException("Query parameter 'page' must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestParameterException(
                    "Query parameter 'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private RestaurantSortOption resolveSortOption(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "rating" -> RestaurantSortOption.RATING;
            case "delivery_time", "delivery-time", "deliverytime" -> RestaurantSortOption.DELIVERY_TIME;
            case "delivery_fee", "delivery-fee", "deliveryfee" -> RestaurantSortOption.DELIVERY_FEE;
            default -> throw new InvalidRequestParameterException(
                    "Invalid 'sort' value: '" + raw + "'. Allowed values: rating, delivery_time, delivery_fee");
        };
    }

    private Sort resolveSort(RestaurantSortOption option) {
        if (option == null) {
            return Sort.by(Sort.Direction.ASC, "id");
        }
        Sort primary = switch (option) {
            case RATING -> Sort.by(Sort.Direction.DESC, "ratingAverage");
            case DELIVERY_TIME -> Sort.by(Sort.Direction.ASC, "estimatedDeliveryMinMinutes");
            case DELIVERY_FEE -> Sort.by(Sort.Direction.ASC, "deliveryFee");
        };
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
