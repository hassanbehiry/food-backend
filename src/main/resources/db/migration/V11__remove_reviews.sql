DROP TABLE IF EXISTS public.reviews;

ALTER TABLE public.restaurants
    DROP CONSTRAINT restaurants_check;

ALTER TABLE public.restaurants
    DROP COLUMN rating_average,
    DROP COLUMN review_count;

ALTER TABLE public.restaurants
    ADD CONSTRAINT restaurants_check CHECK ((estimated_delivery_max_minutes >= estimated_delivery_min_minutes)
        AND (delivery_fee >= (0)::numeric) AND (minimum_order >= (0)::numeric)
        AND ((open_time IS NULL) OR (close_time IS NULL) OR (close_time > open_time)));
