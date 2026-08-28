package com.food.foodapp.auth.controller;

import com.food.foodapp.auth.dto.AdminUserListResponse;
import com.food.foodapp.auth.dto.AdminUserResponse;
import com.food.foodapp.auth.dto.AdminUserStatusUpdateRequest;
import com.food.foodapp.auth.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin user-account management: paginated/filterable listing, detail, and the
 * suspend/reactivate status action. Thin controller — all business rules live in
 * {@link AdminUserService}.
 * <p>
 * NOTE: same authorization gap as {@link com.food.foodapp.restaurant.controller.AdminRestaurantController}
 * — see {@link AdminUserService} for details, including why "an admin cannot suspend their own
 * account" isn't enforced yet.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * GET /api/v1/admin/users
     * Supports independent, composable {@code role} (customer | owner) and {@code status}
     * (active | suspended) filters, plus pagination.
     */
    @GetMapping
    public ResponseEntity<AdminUserListResponse> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.listUsers(role, status, page, size));
    }

    /** GET /api/v1/admin/users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    /** PATCH /api/v1/admin/users/{id}/status — body: {"status": "ACTIVE" | "SUSPENDED"}. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ResponseEntity.ok(adminUserService.updateStatus(id, request.getStatus()));
    }
}
