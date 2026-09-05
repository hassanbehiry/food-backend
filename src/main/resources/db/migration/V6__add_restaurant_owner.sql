-- Restaurant ownership: the user who owns and manages a restaurant.
--
-- Audit §10.2 / DB-3: `Restaurant` had no owner relation, so `/api/v1/owner/**` could not be
-- scoped to the authenticated owner — any caller who guessed a restaurant id could rewrite its
-- menu, prices and hours and drive its orders' status. This column is the anchor for the
-- service-level ownership check (RestaurantOwnershipGuard) and the filter-chain rule that now
-- requires authentication for every owner route.
--
-- owner_id is NULLABLE and NOT backfilled:
--   * The V2-seeded demo restaurants have no natural owner, and inventing credentials for a fake
--     owner account in SQL (bcrypt hashing, a login nobody controls) is worse than leaving them
--     unowned. An unowned restaurant is simply unmanageable through the owner API — every
--     /owner/** call for it returns 403 — until an owner is assigned (a new OWNER registration
--     creates its own restaurant; assigning an existing seeded one is a future admin action).
--   * A NOT NULL constraint is therefore deliberately omitted; it can be added later once every
--     row has an owner.
-- Safe against a populated database: a nullable ADD COLUMN needs no table rewrite and no backfill;
-- the FK only constrains rows that set owner_id.

ALTER TABLE restaurants ADD COLUMN owner_id bigint;

ALTER TABLE restaurants
    ADD CONSTRAINT fk_restaurants_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_restaurants_owner ON restaurants (owner_id);
