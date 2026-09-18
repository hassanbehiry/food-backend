package com.food.foodapp.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void confirmed_canTransitionToPreparingOrCancelled() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void confirmed_cannotSkipStraightToDeliveredOrDispatch() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.READY_FOR_DELIVERY)).isFalse();
    }

    @Test
    void preparing_canTransitionToReadyForDeliveryOrCancelled() {
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY_FOR_DELIVERY)).isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void preparing_cannotSkipStraightToDispatchOrDelivered() {
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void readyForDelivery_canTransitionToOutForDeliveryOrCancelled() {
        assertThat(OrderStatus.READY_FOR_DELIVERY.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(OrderStatus.READY_FOR_DELIVERY.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void readyForDelivery_cannotSkipStraightToDelivered() {
        assertThat(OrderStatus.READY_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.READY_FOR_DELIVERY.canTransitionTo(OrderStatus.PREPARING)).isFalse();
    }

    @Test
    void outForDelivery_canTransitionOnlyToDelivered() {
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.READY_FOR_DELIVERY)).isFalse();
    }

    @Test
    void terminalStatuses_allowNoFurtherTransitions() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void confirmed_cannotTransitionToItself() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }
}
