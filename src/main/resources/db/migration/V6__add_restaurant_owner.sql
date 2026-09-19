-- Restaurant ownership: the user who owns and manages a restaurant.
--
-- Audit §10.2 / DB-3: `Restaurant` had no owner relation, so `/api/v1/owner/**` could not be
-- scoped to the authenticated owner — any caller who guessed a restaurant id could rewrite its
-- menu, prices and hours and drive its orders' status. This column is the anchor for the
-- service-level ownership check (RestaurantOwnershipGuard) and the filter-chain rule that now
-- requires authentication for every owner route.
--
-- owner_id is NULLABLE:
--   * A restaurant with no natural owner is simply unmanageable through the owner API — every
--     /owner/** call for it returns 403 — until an owner is assigned (a new OWNER registration
--     creates its own restaurant; assigning an existing restaurant is otherwise an admin action).
--   * A NOT NULL constraint is therefore deliberately omitted; it can be added later once every
--     row has an owner.
-- Safe against a populated database: a nullable ADD COLUMN needs no table rewrite, and the
-- backfill below only ever touches the one demo restaurant/owner this migration already knows
-- about by name/email — it never invents an owner for a real, user-created restaurant.

ALTER TABLE restaurants ADD COLUMN owner_id bigint;

ALTER TABLE restaurants
    ADD CONSTRAINT fk_restaurants_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_restaurants_owner ON restaurants (owner_id);

-- V2's demo restaurant/owner both exist by the time this migration runs, but owner_id itself
-- didn't exist yet when V2 seeded them, so it could not be set there — this is the same
-- "adjust already-seeded demo data once a later column/value exists" pattern V9 uses for
-- restaurant 5's open/close hours. Guarded by name/email so it only ever links the specific
-- V2-seeded demo restaurant to the specific V2-seeded demo owner, never a real row.
UPDATE restaurants
SET owner_id = (SELECT id FROM users WHERE email = 'owner@wajba.com')
WHERE id = 1
  AND name = 'مطبخ وجبة'
  AND owner_id IS NULL
  AND EXISTS (SELECT 1 FROM users WHERE email = 'owner@wajba.com');
