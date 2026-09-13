-- Reintroduces a kitchen-preparation stage between CONFIRMED and OUT_FOR_DELIVERY (PREPARING,
-- READY_FOR_DELIVERY), and adds the tracking columns/table behind the restaurant-driven delivery
-- dispatch and delivery-confirmation workflow:
--   * orders.sent_to_delivery_at / delivery_person_name — stamped when the restaurant dispatches
--     an order to a courier (OUT_FOR_DELIVERY).
--   * orders.delivered_by — which side (CUSTOMER or OWNER) confirmed delivery.
--   * revenue_transactions — an append-only ledger of recognized revenue, one row per delivered
--     order, alongside (not replacing) the existing live SUM-over-DELIVERED-orders analytics
--     queries. order_id is UNIQUE so the database itself forbids ever recording the same order's
--     revenue twice, even under a concurrent-write race.
--
-- Purely additive: no existing column is dropped, no existing row's status changes, and every new
-- column is nullable (or has a default), so every order already in the table remains valid as-is.
ALTER TABLE public.orders
    DROP CONSTRAINT orders_status_check;

ALTER TABLE public.orders
    ADD CONSTRAINT orders_status_check
    CHECK (((status)::text = ANY ((ARRAY[
        'CONFIRMED'::character varying,
        'PREPARING'::character varying,
        'READY_FOR_DELIVERY'::character varying,
        'OUT_FOR_DELIVERY'::character varying,
        'DELIVERED'::character varying,
        'CANCELLED'::character varying])::text[])));

ALTER TABLE public.orders
    ADD COLUMN sent_to_delivery_at timestamp(6) without time zone,
    ADD COLUMN delivery_person_name varchar(150),
    ADD COLUMN delivered_by varchar(20);

ALTER TABLE public.orders
    ADD CONSTRAINT orders_delivered_by_check
    CHECK ((delivered_by IS NULL) OR ((delivered_by)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'OWNER'::character varying])::text[])));

CREATE TABLE public.revenue_transactions (
    id              bigserial PRIMARY KEY,
    order_id        bigint NOT NULL UNIQUE REFERENCES public.orders(id),
    restaurant_id   bigint NOT NULL REFERENCES public.restaurants(id),
    amount          numeric(10, 2) NOT NULL CHECK (amount >= 0),
    type            varchar(30) NOT NULL DEFAULT 'ORDER_PAYMENT',
    created_at      timestamp(6) without time zone NOT NULL DEFAULT now()
);

CREATE INDEX idx_revenue_transactions_restaurant_id ON public.revenue_transactions (restaurant_id);
