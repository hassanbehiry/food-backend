package com.food.foodapp.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Small body returned by DELETE endpoints instead of a bare {@code 204}, because the frontend
 * service layer calls {@code response.json()} unconditionally after a delete. {@code deleted} is
 * always present; {@code promotedDefaultId} is only set where a delete cascades a change the
 * caller can't otherwise learn without re-fetching (deleting a customer's default address
 * promotes another one).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeletionResponse {

    private final boolean deleted;
    private final Long promotedDefaultId;

    public static DeletionResponse ok() {
        return DeletionResponse.builder().deleted(true).build();
    }

    public static DeletionResponse ok(Long promotedDefaultId) {
        return DeletionResponse.builder().deleted(true).promotedDefaultId(promotedDefaultId).build();
    }
}
