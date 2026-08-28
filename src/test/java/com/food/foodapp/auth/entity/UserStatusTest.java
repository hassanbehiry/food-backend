package com.food.foodapp.auth.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @Test
    void active_canOnlyTransitionToSuspended() {
        assertThat(UserStatus.ACTIVE.canTransitionTo(UserStatus.SUSPENDED)).isTrue();
    }

    @Test
    void suspended_canOnlyTransitionToActive() {
        assertThat(UserStatus.SUSPENDED.canTransitionTo(UserStatus.ACTIVE)).isTrue();
    }

    @Test
    void noStatus_canTransitionToItself() {
        for (UserStatus status : UserStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }
}
