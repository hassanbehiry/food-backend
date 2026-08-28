package com.food.foodapp.restaurant.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantApprovalStatusTest {

    @Test
    void pending_canTransitionToApprovedOrRejected() {
        assertThat(RestaurantApprovalStatus.PENDING.canTransitionTo(RestaurantApprovalStatus.APPROVED)).isTrue();
        assertThat(RestaurantApprovalStatus.PENDING.canTransitionTo(RestaurantApprovalStatus.REJECTED)).isTrue();
    }

    @Test
    void pending_cannotTransitionToSuspended() {
        assertThat(RestaurantApprovalStatus.PENDING.canTransitionTo(RestaurantApprovalStatus.SUSPENDED)).isFalse();
    }

    @Test
    void approved_canOnlyTransitionToSuspended() {
        assertThat(RestaurantApprovalStatus.APPROVED.canTransitionTo(RestaurantApprovalStatus.SUSPENDED)).isTrue();
        assertThat(RestaurantApprovalStatus.APPROVED.canTransitionTo(RestaurantApprovalStatus.REJECTED)).isFalse();
        assertThat(RestaurantApprovalStatus.APPROVED.canTransitionTo(RestaurantApprovalStatus.PENDING)).isFalse();
    }

    @Test
    void rejected_canBeReconsideredAndApproved() {
        assertThat(RestaurantApprovalStatus.REJECTED.canTransitionTo(RestaurantApprovalStatus.APPROVED)).isTrue();
        assertThat(RestaurantApprovalStatus.REJECTED.canTransitionTo(RestaurantApprovalStatus.SUSPENDED)).isFalse();
    }

    @Test
    void suspended_canOnlyBeRestoredToApproved() {
        assertThat(RestaurantApprovalStatus.SUSPENDED.canTransitionTo(RestaurantApprovalStatus.APPROVED)).isTrue();
        assertThat(RestaurantApprovalStatus.SUSPENDED.canTransitionTo(RestaurantApprovalStatus.REJECTED)).isFalse();
        assertThat(RestaurantApprovalStatus.SUSPENDED.canTransitionTo(RestaurantApprovalStatus.PENDING)).isFalse();
    }

    @Test
    void noStatus_canTransitionToItself() {
        for (RestaurantApprovalStatus status : RestaurantApprovalStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }
}
