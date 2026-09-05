package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AdminUserListResponse;
import com.food.foodapp.auth.dto.AdminUserResponse;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.mapper.UserMapper;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.repository.UserSpecifications;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.AdminActionForbiddenException;
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
 * Authorization: {@code /api/v1/admin/**} is gated to {@code ROLE_ADMIN} at the security filter
 * chain, so every caller here is already an authenticated admin. On top of that, the status
 * action refuses to change an admin's own account or any other {@code ADMIN} account, and the
 * listing excludes {@code ADMIN} rows entirely (admins are provisioned out of band and are not
 * managed from this screen).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final UserContext userContext;

    @Transactional(readOnly = true)
    public AdminUserListResponse listUsers(String rawRole, String rawStatus, int page, int size) {
        validatePagination(page, size);
        Role roleFilter = resolveRoleFilter(rawRole);
        UserStatus statusFilter = resolveStatusFilter(rawStatus);

        Specification<User> specification = UserSpecifications.roleNot(Role.ADMIN);
        if (roleFilter != null) {
            specification = specification.and(UserSpecifications.hasRole(roleFilter));
        }
        if (statusFilter != null) {
            specification = specification.and(UserSpecifications.hasStatus(statusFilter));
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
     * The single point every admin status change routes through.
     * <p>
     * Re-applying the status a user is already in is a 200 no-op, not a {@code 409} — a dashboard
     * toggle that re-sends the current state must not surface an error. An admin may not change
     * their own account's status, nor any other {@code ADMIN} account's (both → 403). Logged at
     * info level as a minimal audit trail — this codebase has no persisted audit-log entity yet.
     */
    @Transactional
    public AdminUserResponse updateStatus(Long id, String rawStatus) {
        UserStatus target = resolveStatus(rawStatus);
        User user = requireUser(id);

        if (user.getId().equals(userContext.getCurrentUserId())) {
            throw new AdminActionForbiddenException("An admin cannot change their own account status");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new AdminActionForbiddenException("Another admin's account status cannot be changed here");
        }

        UserStatus current = user.getStatus();
        if (current == target) {
            return UserMapper.toAdminResponse(user);
        }
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
