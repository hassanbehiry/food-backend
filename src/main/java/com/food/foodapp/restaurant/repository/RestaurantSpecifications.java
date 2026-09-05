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

    /**
     * The customer discovery list shows every admin-{@code APPROVED} restaurant, whether or not
     * it is currently accepting orders — a closed-but-approved restaurant is returned with
     * {@code isOpenForOrders:false} so the UI can grey it out rather than being hidden entirely.
     * Detail/menu visibility is a separate rule (see {@code RestaurantService.isCustomerVisible}).
     */
    public static Specification<Restaurant> approvedForCustomerListing() {
        return (root, query, cb) -> cb.equal(root.get("approvalStatus"), RestaurantApprovalStatus.APPROVED);
    }

    public static Specification<Restaurant> nameOrCuisineContains(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("cuisine")), pattern)
        );
    }

    /** Restaurants tagged with the category whose {@code slug} matches (homepage chip / dashboard filter). */
    public static Specification<Restaurant> hasCategorySlug(String slug) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Restaurant, Category> categories = root.join("categories");
            return cb.equal(categories.get("slug"), slug);
        };
    }

    /** Admin restaurant-list filter — unlike {@link #approvedForCustomerListing()}, matches any single status. */
    public static Specification<Restaurant> hasApprovalStatus(RestaurantApprovalStatus status) {
        return (root, query, cb) -> cb.equal(root.get("approvalStatus"), status);
    }
}
