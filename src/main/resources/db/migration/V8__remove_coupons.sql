ALTER TABLE public.orders
    DROP CONSTRAINT orders_check;

ALTER TABLE public.orders
    DROP COLUMN coupon_code,
    DROP COLUMN discount;

ALTER TABLE public.orders
    ADD CONSTRAINT orders_check CHECK (((subtotal >= (0)::numeric) AND (delivery_fee >= (0)::numeric) AND (total >= (0)::numeric)));

DROP TABLE IF EXISTS public.coupon_usages;
DROP TABLE IF EXISTS public.coupons;
