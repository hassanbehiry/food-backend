-- Seed data: platform-wide categories, one flagship demo restaurant with a full menu, and
-- three demo accounts (admin/owner/customer) to log in as immediately after provisioning.
--
-- Categories: names/icons/order are still sourced from the frontend's mock data layer at
-- foodhub-main/src/data/data.js (FH_DATA.categories), so a freshly-provisioned database's
-- discovery chips match what the frontend was built against.
--
-- Restaurant/menu: a single "مطبخ وجبة" restaurant (purpose-built for this seed, not sourced
-- from the frontend fixture) with a real multi-category menu, pre-approved and owned by the
-- demo OWNER account below, so /api/v1/owner/** has something real to demo against on a fresh
-- database without going through manual admin approval first.
--
-- The categories/restaurants/restaurant_categories/menu_categories/menu_items INSERTs are each
-- guarded with "WHERE NOT EXISTS (SELECT 1 FROM <that table>)" so this migration is a safe
-- no-op against a database that already holds data in the target table (an existing install, a
-- shared test database, a re-point at a populated DB). It only ever populates a table that is
-- currently empty, so it can never collide with or overwrite a row that is already there.
--
-- The users and addresses INSERTs are the exception to that pattern: neither table is ever
-- guaranteed empty (real registrations may already exist), so they guard per-row instead — on
-- email for users, on customer_id for the demo customer's address — and never hardcode an id,
-- letting the IDENTITY column auto-assign one so they can't collide with a real row. The three
-- demo emails live on a wajba.com domain, kept out of the way of any real account.
--
-- IDs for categories/restaurants/menu_categories/menu_items are assigned explicitly so the
-- restaurant_categories/menu_categories/menu_items rows can reference them directly and this
-- file reads the same way every time. Each identity sequence is then bumped to at least the
-- highest id present (GREATEST against its current value, so it can never move backwards) to
-- keep application-created rows off the seeded ids.

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

-- ──── Demo accounts (admin / owner / customer) ────
-- Passwords are pre-hashed with jBCrypt (org.mindrot.jbcrypt.BCrypt.hashpw + BCrypt.gensalt(),
-- the same call PasswordEncoder.encode() makes) since a SQL migration cannot invoke the app's
-- Java password encoder at run time. Each hash below was generated and independently
-- re-verified with BCrypt.checkpw() before being embedded here.
--
--   admin@wajba.com    / WajbaAdmin@2026
--   owner@wajba.com    / WajbaOwner@2026
--   customer@wajba.com / WajbaCustomer@2026

INSERT INTO public.users (name, email, password, role, status, created_at, updated_at)
SELECT v.name, v.email, v.password, v.role, 'ACTIVE', now(), now()
FROM (VALUES
    ('مدير المنصة التجريبي', 'admin@wajba.com',
        '$2a$10$kBNVIxlBC8DBss4JQn/p5e0Gnq54JhnfA0r7YanIZuJP30Y8spvJm', 'ADMIN'),
    ('مالك مطبخ وجبة', 'owner@wajba.com',
        '$2a$10$OwWFF2WnvM7AqRivNTI70O8YVkHXIp.w9EeCDQ3IPqux7gIh.p1He', 'OWNER'),
    ('عميل وجبة التجريبي', 'customer@wajba.com',
        '$2a$10$KDJP/ve7Ux5EgAJvKHG62OqlWyXa3SIQ00ssNAHG2A17MnI7Te4aa', 'CUSTOMER')
) AS v(name, email, password, role)
WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE users.email = v.email);

-- ──── Demo customer's saved address ────
-- One seeded delivery address for the demo CUSTOMER account, so checkout has something to
-- select without creating an address by hand first. Guarded per-customer rather than on the
-- whole table being empty — same reasoning as the users insert above: addresses is never
-- guaranteed empty, since a real customer may already have saved one.

INSERT INTO public.addresses (customer_id, label, street, city, postal_code, is_default, created_at, updated_at)
SELECT u.id, 'المنزل', 'شارع الأمير محمد بن عبدالعزيز، مبنى 12، شقة 4', 'الرياض', '12211', true, now(), now()
FROM public.users u
WHERE u.email = 'customer@wajba.com'
  AND NOT EXISTS (SELECT 1 FROM public.addresses a WHERE a.customer_id = u.id);

-- ──── Restaurant ────
-- A single flagship demo restaurant, pre-approved. Only columns that exist in V1 are populated
-- here — this includes minimum_order/rating_average/review_count/is_open_for_orders, which are
-- still NOT NULL at this point in the migration sequence even though later migrations
-- (V9/V11/V17) go on to drop them; leaving them out here would fail the insert, not just leave
-- them unseeded. restaurants.owner_id, conversely, does NOT exist yet (it is added later by V6),
-- so it cannot be set in this file — V6 links this restaurant to the demo OWNER account above
-- once that column exists (see the backfill at the end of V6__add_restaurant_owner.sql), the
-- same way V9 adjusts this same seeded row's hours once is_open_for_orders is replaced by
-- open_time/close_time.

INSERT INTO public.restaurants (
    id, name, cuisine, logo_url, cover_image_url,
    rating_average, review_count, delivery_fee, minimum_order,
    estimated_delivery_min_minutes, estimated_delivery_max_minutes,
    open_time, close_time, is_open_for_orders, approval_status, created_at, updated_at
)
SELECT 1, 'مطبخ وجبة', 'عالمي · بيتزا وبرجر',
       'https://images.unsplash.com/photo-1550547660-d9450f859349?auto=format&fit=crop&w=200&q=80',
       'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80',
       4.80, 642, 9.99, 30.00, 20, 30, '10:00:00', '23:00:00', true, 'APPROVED',
       now(), now()
WHERE NOT EXISTS (SELECT 1 FROM public.restaurants);

SELECT setval('public.restaurants_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.restaurants),
             (SELECT last_value FROM public.restaurants_id_seq)));

-- ──── Restaurant <-> category tags ────
-- Tagged to the two platform categories its own menu tabs actually match (بيتزا, برجر).

INSERT INTO public.restaurant_categories (restaurant_id, category_id)
SELECT v.restaurant_id, v.category_id
FROM (VALUES
    (1, 1), -- مطبخ وجبة -> بيتزا
    (1, 2)  -- مطبخ وجبة -> برجر
) AS v(restaurant_id, category_id)
WHERE NOT EXISTS (SELECT 1 FROM public.restaurant_categories)
  -- only when the restaurant above was actually seeded by this migration
  AND EXISTS (SELECT 1 FROM public.restaurants WHERE id = 1 AND name = 'مطبخ وجبة')
  AND EXISTS (SELECT 1 FROM public.categories WHERE id = v.category_id);

-- ──── Menu categories (tabs) ────

INSERT INTO public.menu_categories (id, restaurant_id, name, display_order, active, created_at, updated_at)
SELECT v.id, v.restaurant_id, v.name, v.display_order, true, now(), now()
FROM (VALUES
    (1, 1, 'بيتزا',           0),
    (2, 1, 'برجر ومشويات',    1),
    (3, 1, 'أطباق جانبية',    2),
    (4, 1, 'مشروبات',         3)
) AS v(id, restaurant_id, name, display_order)
WHERE NOT EXISTS (SELECT 1 FROM public.menu_categories)
  -- only when the restaurant above was actually seeded by this migration
  AND EXISTS (SELECT 1 FROM public.restaurants WHERE id = 1 AND name = 'مطبخ وجبة');

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
    -- بيتزا
    (1, 1, 1, 'مارجريتا كلاسيك', 'صلصة طماطم سان مارزانو، جبنة موزاريلا، ريحان طازج', 47.50,
        'https://images.unsplash.com/photo-1574071318508-1cdbab80d002?auto=format&fit=crop&w=400&q=80', 0),
    (2, 1, 1, 'كواترو فورماجي', 'أربعة أنواع جبن: موزاريلا، جورجونزولا، بارميزان، بروفولوني', 55.00,
        'https://images.unsplash.com/photo-1548369937-47519962c11a?auto=format&fit=crop&w=400&q=80', 1),
    (3, 1, 1, 'ديافولا الحارة', 'سلامي حار، فليفلة مجروشة، موزاريلا، طماطم', 57.50,
        'https://images.unsplash.com/photo-1571066811602-716837d681de?auto=format&fit=crop&w=400&q=80', 2),
    -- برجر ومشويات
    (4, 1, 2, 'طبق الشيف الخاص', 'تشكيلة مميزة من الشيف بمكونات موسمية مختارة بعناية', 60.00,
        'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=400&q=80', 0),
    (5, 1, 2, 'وعاء البيت الخاص', 'حصة كبيرة، تُقدم مع إضافة من اختيارك', 47.50,
        'https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?auto=format&fit=crop&w=400&q=80', 1),
    (6, 1, 2, 'تشكيلة مشاوي', 'تُشوى على الفحم مباشرة وتُقدم سريعًا وهي سخنة', 50.00,
        'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=400&q=80', 2),
    -- أطباق جانبية
    (7, 1, 3, 'فوكاتشا بالثوم', 'تُخبز يوميًا بإكليل الجبل وملح البحر', 22.50,
        'https://images.unsplash.com/photo-1556008531-57e6eefc7be4?auto=format&fit=crop&w=400&q=80', 0),
    (8, 1, 3, 'بطاطس مقرمشة', 'تُقطّع يدويًا وتُقلى مرتين مع ملح البحر', 17.50,
        'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=400&q=80', 1),
    -- مشروبات
    (9, 1, 4, 'سان بيليجرينو', 'مياه غازية 330 مل', 10.00,
        'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=400&q=80', 0),
    (10, 1, 4, 'ليموناضة طازجة', 'معصورة على البارد وبدون أي سكر مضاف', 12.50,
        'https://images.unsplash.com/photo-1621263764928-df1444c5e859?auto=format&fit=crop&w=400&q=80', 1)
) AS v(id, restaurant_id, category_id, name, description, price, image_url, display_order)
WHERE NOT EXISTS (SELECT 1 FROM public.menu_items)
  AND EXISTS (SELECT 1 FROM public.menu_categories WHERE id = v.category_id);

SELECT setval('public.menu_items_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM public.menu_items),
             (SELECT last_value FROM public.menu_items_id_seq)));
