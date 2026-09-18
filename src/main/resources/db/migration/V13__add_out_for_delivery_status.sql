-- Reintroduces OUT_FOR_DELIVERY between CONFIRMED and DELIVERED, so a restaurant handing an order
-- to a courier is distinguishable from the customer actually confirming receipt, and adds
-- delivered_at to record the moment that confirmation happens. Every order already in progress is
-- still CONFIRMED (V12 collapsed NEW/PREPARING/ON_THE_WAY into it), so no data backfill is needed.
ALTER TABLE public.orders
    ADD COLUMN delivered_at timestamp(6) without time zone;

ALTER TABLE public.orders
    DROP CONSTRAINT orders_status_check;

ALTER TABLE public.orders
    ADD CONSTRAINT orders_status_check
    CHECK (((status)::text = ANY ((ARRAY['CONFIRMED'::character varying, 'OUT_FOR_DELIVERY'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])));
