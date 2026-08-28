package com.food.foodapp.restaurant.repository;

import com.food.foodapp.category.entity.Category;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable, composable query-filter building blocks for {@link Restaurant}.
 * Deciding which of these to combine for a given request is a service-layer concern.
 */
public final class RestaurantSpecifications {

    private RestaurantSpecifications() {
    }

    /** APPROVED by admin and currently accepting orders — the definition of "customer-visible". */
    public static Specification<Restaurant> isCustomerVisible() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("approvalStatus"), RestaurantApprovalStatus.APPROVED),
                cb.isTrue(root.get("openForOrders"))
        );
    }

    public static Specification<Restaurant> nameOrCuisineContains(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("cuisine")), pattern)
        );
    }

    public static Specification<Restaurant> hasCategoryId(Long categoryId) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Restaurant, Category> categories = root.join("categories");
            return cb.equal(categories.get("id"), categoryId);
        };
    }

    /** Admin restaurant-list filter — unlike {@link #isCustomerVisible()}, ignores {@code openForOrders}. */
    public static Specification<Restaurant> hasApprovalStatus(RestaurantApprovalStatus status) {
        return (root, query, cb) -> cb.equal(root.get("approvalStatus"), status);
    }
}
