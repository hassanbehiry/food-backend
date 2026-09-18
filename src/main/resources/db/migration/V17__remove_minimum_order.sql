-- minimumOrder is no longer part of the restaurant model; drop the column and its check-constraint
-- clause (see V11__remove_reviews.sql for the same drop-constraint/drop-column/re-add-constraint
-- pattern, needed because the constraint references the column being dropped).
ALTER TABLE public.restaurants
    DROP CONSTRAINT restaurants_check;

ALTER TABLE public.restaurants
    DROP COLUMN minimum_order;

ALTER TABLE public.restaurants
    ADD CONSTRAINT restaurants_check CHECK ((estimated_delivery_max_minutes >= estimated_delivery_min_minutes)
        AND (delivery_fee >= (0)::numeric)
        AND ((open_time IS NULL) OR (close_time IS NULL) OR (close_time > open_time)));
