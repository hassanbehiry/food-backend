package com.food.foodapp.support;

import com.food.foodapp.restaurant.entity.Restaurant;

import java.time.LocalTime;

/**
 * Gives a {@link Restaurant} fixture business hours that deterministically exclude the current
 * wall-clock time, for tests that need a restaurant to be "closed right now" now that openness
 * is computed from open_time/close_time rather than a stored flag. The window is always on the
 * opposite half of the day from {@link LocalTime#now()}, so it can never accidentally contain
 * "now" and never crosses midnight (avoiding the entity's {@code closeTime > openTime} check).
 */
public final class ClosedHoursFixture {

    private ClosedHoursFixture() {
    }

    public static void makeClosedRightNow(Restaurant restaurant) {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.NOON)) {
            restaurant.setOpenTime(LocalTime.of(13, 0));
            restaurant.setCloseTime(LocalTime.of(14, 0));
        } else {
            restaurant.setOpenTime(LocalTime.of(1, 0));
            restaurant.setCloseTime(LocalTime.of(2, 0));
        }
    }
}
