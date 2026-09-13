-- Widen categories.icon from varchar(255), sized only for short FontAwesome class tokens (e.g.
-- 'fa-pizza-slice'), so it can also comfortably hold an http(s):// image URL, matching
-- restaurants.logo_url/cover_image_url (both varchar(500)). The frontend now renders whichever
-- shape a category's icon value is.

ALTER TABLE public.categories ALTER COLUMN icon TYPE varchar(500);
