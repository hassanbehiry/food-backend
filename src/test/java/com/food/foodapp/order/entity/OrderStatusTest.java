package com.food.foodapp.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void newAndConfirmed_canTransitionToCancelled() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void oncePreparingHasStarted_cancellationIsNoLongerAllowed() {
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.ON_THE_WAY.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStatuses_allowNoFurtherTransitions() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void happyPath_progressesForwardOneStepAtATime() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING)).isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.ON_THE_WAY)).isTrue();
        assertThat(OrderStatus.ON_THE_WAY.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void statusCannotSkipAhead() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PREPARING)).isFalse();
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }
}
