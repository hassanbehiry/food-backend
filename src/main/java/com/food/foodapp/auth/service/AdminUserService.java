package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AdminUserListResponse;
import com.food.foodapp.auth.dto.AdminUserResponse;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.mapper.UserMapper;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.repository.UserSpecifications;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.InvalidUserStatusTransitionException;
import com.food.foodapp.common.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Platform-admin user-account management: paginated/filterable listing, detail, and
 * suspend/reactivate status changes — the backend for the admin dashboard's Users tab
 * (All / Customers / Owners / Suspended).
 * <p>
 * NOTE: same authorization gap as {@link com.food.foodapp.restaurant.controller.AdminRestaurantController}
 * — this codebase has no admin-authentication middleware yet (no {@code ADMIN} role, no Spring
 * Security). In particular, "an admin cannot suspend their own account" cannot be enforced here:
 * there is no way to resolve which authenticated user is making this request. This is deferred
 * until admin identity can be resolved, the same way that controller's authorization gap is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminUserListResponse listUsers(String rawRole, String rawStatus, int page, int size) {
        validatePagination(page, size);
        Role roleFilter = resolveRoleFilter(rawRole);
        UserStatus statusFilter = resolveStatusFilter(rawStatus);

        Specification<User> specification = null;
        if (roleFilter != null) {
            specification = UserSpecifications.hasRole(roleFilter);
        }
        if (statusFilter != null) {
            specification = specification == null
                    ? UserSpecifications.hasStatus(statusFilter)
                    : specification.and(UserSpecifications.hasStatus(statusFilter));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<User> result = userRepository.findAll(specification, pageable);

        List<AdminUserResponse> users = result.getContent().stream()
                .map(UserMapper::toAdminResponse)
                .toList();

        return AdminUserListResponse.builder()
                .users(users)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        return UserMapper.toAdminResponse(requireUser(id));
    }

    /**
     * The single point every admin status change routes through, so the legal-transition rules
     * in {@link UserStatus} are enforced in one place. Logged at info level as a minimal audit
     * trail — this codebase has no persisted audit-log entity yet.
     */
    @Transactional
    public AdminUserResponse updateStatus(Long id, String rawStatus) {
        UserStatus target = resolveStatus(rawStatus);
        User user = requireUser(id);
        UserStatus current = user.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new InvalidUserStatusTransitionException(
                    "User " + id + " cannot move from " + current + " to " + target);
        }
        user.setStatus(target);
        User saved = userRepository.save(user);
        log.info("User {} status changed from {} to {}", id, current, target);
        return UserMapper.toAdminResponse(saved);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    private Role resolveRoleFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestParameterException("Invalid 'role' value: '" + raw + "'");
        }
    }

    private UserStatus resolveStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return resolveStatus(raw);
    }

    private UserStatus resolveStatus(String raw) {
        try {
            return UserStatus.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestParameterException("Invalid 'status' value: '" + raw + "'");
        }
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
}
