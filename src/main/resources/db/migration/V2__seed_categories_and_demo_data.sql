-- Seed data: platform-wide categories + a handful of demo restaurants/menus.
--
-- Source of truth for names/icons/order: the frontend's mock data layer at
-- foodhub-main/src/data/data.js (FH_DATA.categories / FH_DATA.restaurants / FH_DATA.menus),
-- so a freshly-provisioned database renders the same homepage/dashboard content the frontend
-- was built against, rather than an empty list.
--
-- Only columns that exist in V1 are populated here. categories.slug and restaurants.owner_id
-- are intentionally NOT seeded — those columns do not exist yet (planned for a later
-- migration) and this seed must not invent data for a column that isn't there.
--
-- Every INSERT is guarded with "WHERE NOT EXISTS (SELECT 1 FROM <that table>)" so this
-- migration is a safe no-op against a database that already holds data in the target table
-- (an existing install, a shared test database, a re-point at a populated DB). It only ever
-- populates a table that is currently empty, so it can never collide with or overwrite a row
-- that is already there. A fresh/empty database gets the full data set.
--
-- IDs are assigned explicitly so the restaurant_categories / menu_categories / menu_items
-- rows can reference them directly and this file reads the same way every time. Each identity
-- sequence is then bumped to at least the highest id present (GREATEST against its current
-- value, so it can never move backwards) to keep application-created rows off the seeded ids.

-- ──── Categories (homepage discovery chips) ────
-- icon values are FontAwesome (free, solid-style) class tokens, matching what the
-- frontend's icon-chip rendering expects (e.g. class "fa-solid" plus this token).

INSERT INTO public.categories (id, name, icon, created_at, updated_at)
SELECT v.id, v.name, v.icon, now(), now()
FROM (VALUES
    (1, 'بيتزا',           'fa-pizza-slice'),
    (2, 'برجر',            'fa-burger'),
    (3, 'سوشي',            'fa-fish'),
    (4, 'مأكولات عربية',    'fa-bowl-food'),
    (5, 'حلويات',          'fa-ice-cream'),
    (6, 'أكل صحي',         'fa-leaf'),
    (7, 'آسيوي',           'fa-pepper-hot'),
    (8, 'فطور',            'fa-mug-saucer')
) AS v(id, name, icon)
WHERE NOT EXISTS (SELECT 1 FROM public.categories);

SELECT setval('public.categories_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.categories),
             (SELECT last_value FROM public.categories_id_seq)));

-- ──── Restaurants ────
-- approval_status is APPROVED and is_open_for_orders mirrors the frontend fixture's
-- isOpen flag (restaurant 5 is seeded closed on purpose, matching the source fixture, so it
-- is correctly excluded from the customer-visible listing).

INSERT INTO public.restaurants (
    id, name, cuisine, logo_url, cover_image_url,
    rating_average, review_count, delivery_fee, minimum_order,
    estimated_delivery_min_minutes, estimated_delivery_max_minutes,
    is_open_for_orders, approval_status, created_at, updated_at
)
SELECT v.id, v.name, v.cuisine, v.logo_url, v.cover_image_url,
       v.rating_average, v.review_count, v.delivery_fee, v.minimum_order,
       v.est_min, v.est_max, v.is_open, 'APPROVED', now(), now()
FROM (VALUES
    (1, 'بيلا نابولي للبيتزا', 'إيطالي · بيتزا',
        'https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=80',
        4.80, 642, 12.50, 40.00, 25, 35, true),
    (2, 'سموك هاوس للبرجر', 'أمريكي · برجر',
        'https://images.unsplash.com/photo-1571997478779-2adcbbe9ab2f?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1571091718767-18b5b1457add?auto=format&fit=crop&w=900&q=80',
        4.60, 418, 10.00, 30.00, 20, 30, true),
    (3, 'ساكورا للسوشي', 'ياباني · سوشي',
        'https://images.unsplash.com/photo-1611143669185-af224c5e3252?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1553621042-f6e147245754?auto=format&fit=crop&w=900&q=80',
        4.90, 301, 17.50, 50.00, 30, 40, true),
    (4, 'جرين بول للأكل الصحي', 'صحي · سلطات',
        'https://images.unsplash.com/photo-1543353071-087092ec393a?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=900&q=80',
        4.50, 212, 7.50, 25.00, 15, 25, true),
    (5, 'التنين الذهبي', 'صيني · آسيوي',
        'https://images.unsplash.com/photo-1496116218417-1a781b1c416c?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1585032226651-759b368d7246?auto=format&fit=crop&w=900&q=80',
        4.40, 356, 10.00, 30.00, 30, 45, false),
    (6, 'سويت كرامبز للحلويات', 'حلويات · مخبوزات',
        'https://images.unsplash.com/photo-1517433367423-c7e5b0f35086?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1551024601-bec78aea704b?auto=format&fit=crop&w=900&q=80',
        4.90, 530, 10.00, 20.00, 20, 30, true),
    (7, 'نادي صن رايز للفطور', 'فطور · كافيه',
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=200&q=80',
        'https://images.unsplash.com/photo-1533920379810-6bedac961555?auto=format&fit=crop&w=900&q=80',
        4.60, 178, 7.50, 25.00, 15, 25, true)
) AS v(id, name, cuisine, logo_url, cover_image_url,
       rating_average, review_count, delivery_fee, minimum_order,
       est_min, est_max, is_open)
WHERE NOT EXISTS (SELECT 1 FROM public.restaurants);

SELECT setval('public.restaurants_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.restaurants),
             (SELECT last_value FROM public.restaurants_id_seq)));

-- ──── Restaurant <-> category tags ────

INSERT INTO public.restaurant_categories (restaurant_id, category_id)
SELECT v.restaurant_id, v.category_id
FROM (VALUES
    (1, 1), -- بيلا نابولي للبيتزا -> بيتزا
    (2, 2), -- سموك هاوس للبرجر -> برجر
    (3, 3), -- ساكورا للسوشي -> سوشي
    (3, 7), -- ساكورا للسوشي -> آسيوي
    (4, 6), -- جرين بول للأكل الصحي -> أكل صحي
    (5, 7), -- التنين الذهبي -> آسيوي
    (6, 5), -- سويت كرامبز للحلويات -> حلويات
    (7, 8)  -- نادي صن رايز للفطور -> فطور
) AS v(restaurant_id, category_id)
WHERE NOT EXISTS (SELECT 1 FROM public.restaurant_categories)
  -- only when the restaurants above were actually seeded by this migration
  AND EXISTS (SELECT 1 FROM public.restaurants
              WHERE id = 1 AND name = 'بيلا نابولي للبيتزا')
  AND EXISTS (SELECT 1 FROM public.categories WHERE id = v.category_id);

-- ──── Menu categories (tabs) ────

INSERT INTO public.menu_categories (id, restaurant_id, name, display_order, active, created_at, updated_at)
SELECT v.id, v.restaurant_id, v.name, v.display_order, true, now(), now()
FROM (VALUES
    -- restaurant 1: بيلا نابولي للبيتزا
    (1, 1, 'بيتزا',           0),
    (2, 1, 'باستا',           1),
    (3, 1, 'أطباق جانبية',    2),
    (4, 1, 'مشروبات',         3),
    -- restaurant 2: سموك هاوس للبرجر
    (5, 2, 'الأكثر طلبًا',     0),
    (6, 2, 'أطباق رئيسية',    1),
    (7, 2, 'أطباق جانبية',    2),
    (8, 2, 'مشروبات',         3)
) AS v(id, restaurant_id, name, display_order)
WHERE NOT EXISTS (SELECT 1 FROM public.menu_categories)
  -- only when the restaurants above were actually seeded by this migration
  AND EXISTS (SELECT 1 FROM public.restaurants
              WHERE id = 1 AND name = 'بيلا نابولي للبيتزا');

SELECT setval('public.menu_categories_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.menu_categories),
             (SELECT last_value FROM public.menu_categories_id_seq)));

-- ──── Menu items ────

INSERT INTO public.menu_items (
    id, restaurant_id, category_id, name, description, price, image_url,
    display_order, available, created_at, updated_at
)
SELECT v.id, v.restaurant_id, v.category_id, v.name, v.description, v.price, v.image_url,
       v.display_order, true, now(), now()
FROM (VALUES
    -- restaurant 1: بيلا نابولي للبيتزا
    (1, 1, 1, 'مارجريتا كلاسيك', 'صلصة طماطم سان مارزانو، جبنة موزاريلا، ريحان طازج', 47.50,
        'https://images.unsplash.com/photo-1574071318508-1cdbab80d002?auto=format&fit=crop&w=400&q=80', 0),
    (2, 1, 1, 'كواترو فورماجي', 'أربعة أنواع جبن: موزاريلا، جورجونزولا، بارميزان، بروفولوني', 55.00,
        'https://images.unsplash.com/photo-1593560708920-61b98ae243f7?auto=format&fit=crop&w=400&q=80', 1),
    (3, 1, 1, 'ديافولا الحارة', 'سلامي حار، فليفلة مجروشة، موزاريلا، طماطم', 57.50,
        'https://images.unsplash.com/photo-1571066811602-716837d681de?auto=format&fit=crop&w=400&q=80', 2),
    (4, 1, 2, 'باستا كاربونارا', 'جوانشاله، صفار بيض، جبنة بيكورينو، فلفل أسود', 52.50,
        'https://images.unsplash.com/photo-1612874742237-6526221588e3?auto=format&fit=crop&w=400&q=80', 0),
    (5, 1, 3, 'فوكاتشا بالثوم', 'تُخبز يوميًا بإكليل الجبل وملح البحر', 22.50,
        'https://images.unsplash.com/photo-1619985632461-f33748a05f44?auto=format&fit=crop&w=400&q=80', 0),
    (6, 1, 4, 'سان بيليجرينو', 'مياه غازية 330 مل', 10.00,
        'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=400&q=80', 0),
    -- restaurant 2: سموك هاوس للبرجر
    (7, 2, 5, 'طبق الشيف الخاص', 'تشكيلة مميزة من الشيف بمكونات موسمية مختارة بعناية', 60.00,
        'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=400&q=80', 0),
    (8, 2, 6, 'وعاء البيت الخاص', 'حصة كبيرة، تُقدم مع إضافة من اختيارك', 47.50,
        'https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?auto=format&fit=crop&w=400&q=80', 0),
    (9, 2, 6, 'تشكيلة مشاوي', 'تُشوى على الفحم مباشرة وتُقدم سريعًا وهي سخنة', 50.00,
        'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=400&q=80', 1),
    (10, 2, 7, 'بطاطس مقرمشة', 'تُقطّع يدويًا وتُقلى مرتين مع ملح البحر', 17.50,
        'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=400&q=80', 0),
    (11, 2, 8, 'ليموناضة طازجة', 'معصورة على البارد وبدون أي سكر مضاف', 12.50,
        'https://images.unsplash.com/photo-1621263764928-df1444c5e859?auto=format&fit=crop&w=400&q=80', 0)
) AS v(id, restaurant_id, category_id, name, description, price, image_url, display_order)
WHERE NOT EXISTS (SELECT 1 FROM public.menu_items)
  AND EXISTS (SELECT 1 FROM public.menu_categories WHERE id = v.category_id);

SELECT setval('public.menu_items_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.menu_items),
             (SELECT last_value FROM public.menu_items_id_seq)));
