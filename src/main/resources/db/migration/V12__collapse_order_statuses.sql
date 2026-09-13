-- Collapses the order lifecycle down to CONFIRMED / DELIVERED / CANCELLED: NEW, PREPARING and
-- ON_THE_WAY are no longer distinct statuses. Every order still in one of those three in-progress
-- states is now simply CONFIRMED (it hasn't reached a terminal state yet); DELIVERED and CANCELLED
-- orders are untouched.
UPDATE public.orders
    SET status = 'CONFIRMED'
    WHERE status IN ('NEW', 'PREPARING', 'ON_THE_WAY');

ALTER TABLE public.orders
    DROP CONSTRAINT orders_status_check;

ALTER TABLE public.orders
    ADD CONSTRAINT orders_status_check
    CHECK (((status)::text = ANY ((ARRAY['CONFIRMED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])));
