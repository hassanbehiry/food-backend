-- Whether a restaurant is "open" is now computed entirely from open_time/close_time
-- (Restaurant.isCurrentlyOpen()) instead of this separate owner-settable flag.
ALTER TABLE public.restaurants
    DROP COLUMN is_open_for_orders;

-- Restaurant 5 was previously seeded "closed" via the flag this migration removes. Give it a
-- narrow overnight window instead, so the demo still shows a closed restaurant most of the day.
UPDATE public.restaurants
SET open_time = '03:00:00', close_time = '04:00:00'
WHERE id = 5;
