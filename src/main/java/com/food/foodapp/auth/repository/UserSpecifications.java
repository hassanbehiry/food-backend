package com.food.foodapp.auth.repository;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable, composable query-filter building blocks for {@link User}.
 * Deciding which of these to combine for a given request is a service-layer concern.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<User> roleNot(Role role) {
        return (root, query, cb) -> cb.notEqual(root.get("role"), role);
    }
}
