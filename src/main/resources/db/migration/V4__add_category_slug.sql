-- Add a stable, URL-safe string identifier (slug) to platform categories.
--
-- The frontend keys categories by slug everywhere: the homepage discovery chips put
-- ?category=<slug> in the URL and the dashboard filters restaurants with
-- r.categoryIds.includes(<slug>) (foodhub-main/src/data/data.js FH_DATA.categories[].id).
-- Until now `categories` had only a numeric IDENTITY id, so GET /restaurants?category=pizza
-- (a slug) hit the Long-typed query param and 400'd.
--
-- Safe against a database that already holds category rows (the 8 V2-seeded ones and any
-- others): the column is added nullable, every existing row is backfilled before the
-- NOT NULL + UNIQUE constraints are added. The 8 known categories are matched by their
-- Arabic name to the exact slug the frontend uses; any other/unknown row gets a
-- deterministic 'category-<id>' fallback so the constraints can always be applied.

ALTER TABLE public.categories ADD COLUMN slug varchar(100);

-- Backfill the 8 known platform categories (name -> frontend slug).
UPDATE public.categories SET slug = 'pizza'     WHERE name = 'بيتزا'          AND slug IS NULL;
UPDATE public.categories SET slug = 'burgers'   WHERE name = 'برجر'           AND slug IS NULL;
UPDATE public.categories SET slug = 'sushi'     WHERE name = 'سوشي'           AND slug IS NULL;
UPDATE public.categories SET slug = 'arabic'    WHERE name = 'مأكولات عربية'   AND slug IS NULL;
UPDATE public.categories SET slug = 'desserts'  WHERE name = 'حلويات'          AND slug IS NULL;
UPDATE public.categories SET slug = 'healthy'   WHERE name = 'أكل صحي'         AND slug IS NULL;
UPDATE public.categories SET slug = 'asian'     WHERE name = 'آسيوي'           AND slug IS NULL;
UPDATE public.categories SET slug = 'breakfast' WHERE name = 'فطور'            AND slug IS NULL;

-- Deterministic fallback for any row not covered above, so NOT NULL + UNIQUE are always safe.
UPDATE public.categories SET slug = 'category-' || id WHERE slug IS NULL;

ALTER TABLE public.categories ALTER COLUMN slug SET NOT NULL;
ALTER TABLE public.categories ADD CONSTRAINT categories_slug_key UNIQUE (slug);
