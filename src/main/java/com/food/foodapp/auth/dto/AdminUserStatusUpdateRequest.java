package com.food.foodapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Target status, e.g. {@code "ACTIVE"} or {@code "SUSPENDED"} — parsed and validated against {@code UserStatus} in the service layer, matching how order/restaurant status updates are handled in this codebase. */
@Getter
@Setter
public class AdminUserStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
