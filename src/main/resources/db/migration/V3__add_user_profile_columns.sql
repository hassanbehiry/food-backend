-- User profile columns: phone + avatar_url.
--
-- GAP-006 / DB-1 / DB-2: the frontend RegisterPage collects a phone number and the
-- ProfilePage reads/writes {name, email, phone, avatar}, but the users table only has
-- id, name, email, password, role, status, created_at, updated_at — there is no column
-- anywhere for a user's phone to land in, and RegisterRequest silently drops it.
--
-- Both columns are NULLABLE:
--   * Existing users (including the demo/admin accounts) have neither value and there is
--     no meaningful backfill for a phone number, so a NOT NULL constraint here would fail
--     the ALTER against any populated database and break every existing caller/test that
--     registers without a phone.
--   * The frontend wants phone; the backend persists it when present and returns null when
--     it was never supplied.
--
-- Safe against a database that already holds rows: ADD COLUMN of a nullable column takes no
-- table rewrite and needs no backfill. IF NOT EXISTS makes the migration a no-op on a
-- database where a pre-Flyway ddl-auto=update run had already added either column.

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone varchar(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url varchar(512);
