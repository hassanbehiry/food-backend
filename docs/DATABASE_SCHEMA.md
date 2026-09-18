# Database Schema & Entity Relationships

This document explains how the tables created by the Flyway migrations under
`src/main/resources/db/migration` (`V1` baseline through `V15`) relate to each other. It reflects
the schema **as it stands today** — i.e. after every additive and destructive migration has been
applied (`coupons`, `coupon_usages`, `reviews`, and `password_reset_tokens` were all introduced and
later dropped; they are not part of the current schema and are called out at the end instead of
listed as live tables).

## 1. Entity-Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ ADDRESSES : "has saved"
    USERS ||--o| CARTS : "owns (1:1)"
    USERS ||--o{ ORDERS : "places"
    USERS ||--o{ FAVORITES : "favorites"
    USERS ||--o{ RESTAURANTS : "owns (OWNER role)"

    RESTAURANTS ||--o{ MENU_CATEGORIES : "organizes menu into"
    RESTAURANTS ||--o{ MENU_ITEMS : "sells"
    RESTAURANTS ||--o{ ORDERS : "receives"
    RESTAURANTS ||--o{ FAVORITES : "is favorited via"
    RESTAURANTS ||--o{ REVENUE_TRANSACTIONS : "earns"
    RESTAURANTS }o--o{ CATEGORIES : "tagged with (restaurant_categories)"
    RESTAURANTS ||--o| CARTS : "is the single restaurant of"

    MENU_CATEGORIES ||--o{ MENU_ITEMS : "groups"

    CARTS ||--o{ CART_ITEMS : "contains"
    CART_ITEMS }o--|| MENU_ITEMS : "references"

    ORDERS ||--o{ ORDER_ITEMS : "contains (snapshot)"
    ORDER_ITEMS }o--|| MENU_ITEMS : "priced from"
    ORDERS ||--o| REVENUE_TRANSACTIONS : "recognizes (on DELIVERED)"

    USERS {
        bigint id PK
        varchar email UK
        varchar password
        varchar role "CUSTOMER|OWNER|ADMIN"
        varchar status "ACTIVE|SUSPENDED"
        varchar phone
        varchar avatar_url
    }
    ADDRESSES {
        bigint id PK
        bigint customer_id FK
        varchar city
        varchar street
        boolean is_default
    }
    RESTAURANTS {
        bigint id PK
        bigint owner_id FK "nullable"
        varchar name
        varchar approval_status "PENDING|APPROVED|REJECTED|SUSPENDED"
        numeric delivery_fee
        time open_time
        time close_time
    }
    CATEGORIES {
        bigint id PK
        varchar name UK
        varchar slug UK
        varchar icon
    }
    RESTAURANT_CATEGORIES {
        bigint restaurant_id PK_FK
        bigint category_id PK_FK
    }
    MENU_CATEGORIES {
        bigint id PK
        bigint restaurant_id FK
        varchar name
        int display_order
        boolean active
    }
    MENU_ITEMS {
        bigint id PK
        bigint restaurant_id FK
        bigint category_id FK
        varchar name
        numeric price
        boolean available
    }
    CARTS {
        bigint id PK
        bigint customer_id FK_UK "1:1 with users"
        bigint restaurant_id FK "nullable, single-restaurant cart"
    }
    CART_ITEMS {
        bigint id PK
        bigint cart_id FK
        bigint menu_item_id FK
        int quantity "1..50"
    }
    ORDERS {
        bigint id PK
        varchar order_number UK
        bigint customer_id FK
        bigint restaurant_id FK
        varchar status "CONFIRMED|PREPARING|READY_FOR_DELIVERY|OUT_FOR_DELIVERY|DELIVERED|CANCELLED"
        varchar payment_method "CASH_ON_DELIVERY"
        numeric subtotal
        numeric delivery_fee
        numeric total
        timestamp sent_to_delivery_at
        timestamp delivered_at
        varchar delivered_by "CUSTOMER|OWNER"
    }
    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint menu_item_id FK
        varchar name "snapshot"
        numeric unit_price "snapshot"
        numeric line_total
    }
    FAVORITES {
        bigint id PK
        bigint customer_id FK
        bigint restaurant_id FK
    }
    REVENUE_TRANSACTIONS {
        bigint id PK
        bigint order_id FK_UK "1:1 with orders"
        bigint restaurant_id FK
        numeric amount
        varchar type
    }
    PLATFORM_SETTINGS {
        bigint id PK
        numeric commission_percentage
        numeric default_delivery_fee
        boolean maintenance_mode
        boolean allow_restaurant_registration
    }
```

`PLATFORM_SETTINGS` is a singleton configuration table with no foreign keys in or out — it is
omitted from the relationship arrows above but included for completeness.

## 2. Table-by-Table Reference

### `users`

| Column | Type | Notes |
|---|---|---|
| `id` | `bigint` PK | |
| `email` | `varchar(150)` | `UNIQUE`. Login identifier. |
| `password` | `varchar(255)` | Bcrypt hash. |
| `role` | `varchar(20)` | `CHECK IN (CUSTOMER, OWNER, ADMIN)`. Drives every route guard in `SecurityConfig`. |
| `status` | `varchar(20)` | `CHECK IN (ACTIVE, SUSPENDED)`, default `ACTIVE`. A `SUSPENDED` user is rejected at login. |
| `phone`, `avatar_url` | `varchar` | Nullable profile fields (`V3`). |

One `users` row can simultaneously be the customer behind many `orders`/`addresses`/`favorites`
**and**, if `role = OWNER`, the owner behind one or more `restaurants` — the schema does not force a
user into a single role-shaped relationship set.

### `addresses`

| Column | Notes |
|---|---|
| `customer_id` | `FK -> users.id`. **Many-to-one**: a customer has many saved addresses. |
| `is_default` | Boolean flag; enforced as "at most one default" at the service layer, not by a DB constraint. |

### `restaurants`

| Column | Notes |
|---|---|
| `owner_id` | `FK -> users.id`, **nullable** (`V6`). Nullable because the `V2`-seeded demo restaurants predate the ownership model and have no real owner account to attach; every `/owner/**` endpoint requires a non-null, matching `owner_id` via `RestaurantOwnershipGuard`. |
| `approval_status` | `CHECK IN (PENDING, APPROVED, REJECTED, SUSPENDED)`. Gates public visibility — see `USE_CASES.md` §Admin. |
| `open_time` / `close_time` | Nullable `time`; "currently open" is computed from these at read time (`is_open_for_orders` was dropped in `V9` in favor of this derived check). |
| `rating_average` / `review_count` | **Removed in `V11`** alongside the `reviews` table — see §4. |

### `categories` / `restaurant_categories`

`categories` is a small, platform-managed lookup table (cuisine tags such as "Pizza", "Burgers").
`restaurant_categories` is a pure join table (composite PK `(restaurant_id, category_id)`,
no surrogate `id`) implementing the **many-to-many** between `restaurants` and `categories`: one
restaurant can carry several cuisine tags, and one tag applies to many restaurants.

### `menu_categories` / `menu_items`

- `menu_categories.restaurant_id -> restaurants.id`: **one-to-many** — each restaurant defines its
  own named sections (e.g. "Starters", "Mains"), unique per restaurant (`UNIQUE(restaurant_id,
  name)`).
- `menu_items.restaurant_id -> restaurants.id` **and** `menu_items.category_id ->
  menu_categories.id`: a menu item belongs to exactly one restaurant and exactly one of that
  restaurant's categories (**one-to-many** from each side). The redundant `restaurant_id` on
  `menu_items` (derivable via `category_id`) exists so ownership and availability queries don't need
  a join through `menu_categories`.

### `carts` / `cart_items`

- `carts.customer_id` is `UNIQUE` — **one-to-one** with `users`: every customer has at most one
  persistent cart row, ever (created lazily on first add-to-cart).
- `carts.restaurant_id` is nullable and **single-valued** — a cart holds items from only one
  restaurant at a time; adding an item from a different restaurant requires clearing the cart first
  (`CartRestaurantConflictException`).
- `cart_items` is the join between a cart and the menu items in it (**one-to-many** from `carts`,
  **many-to-one** to `menu_items`), unique per `(cart_id, menu_item_id)` so re-adding the same item
  increments `quantity` instead of duplicating a row. `ON DELETE CASCADE` from `menu_items` means a
  deleted menu item silently drops out of everyone's cart rather than orphaning rows.

### `orders` / `order_items`

- `orders.customer_id -> users.id` and `orders.restaurant_id -> restaurants.id`: **many-to-one** on
  both sides — a customer places many orders over time; a restaurant receives many orders.
- `order_items` **snapshots** `menu_items` at checkout time (`name`, `unit_price`, `image_url` are
  copied, not joined live) so a later price change or deletion on `menu_items` never rewrites
  history for an already-placed order — `order_items.menu_item_id` is kept only for traceability,
  not as the source of truth for what the customer paid.
- `orders.status` is the single source of truth for the order lifecycle documented in
  `DELIVERY_WORKFLOW.md` §1; `delivered_at`/`delivered_by`/`sent_to_delivery_at`/
  `delivery_person_name` are timestamps/labels stamped as the order moves through that lifecycle.
- The delivery address is **denormalized onto the order** (`delivery_street`, `delivery_city`,
  etc.) rather than a live FK to `addresses`, for the same reason as `order_items`: if the customer
  later edits or deletes that saved address, the order must still show what was delivered where.

### `revenue_transactions`

**One-to-one with `orders`** (`order_id` is `NOT NULL UNIQUE`): each order produces at most one
revenue ledger row, written the moment it reaches `DELIVERED` via the restaurant-confirmed path
(`OrderService.deliverOrder`). See `DELIVERY_WORKFLOW.md` §4 for the full write/idempotency story;
this table's role in the schema is simply an auditable, append-only companion to the live
`SUM(total) WHERE status = DELIVERED` queries `OrderAnalyticsService` runs directly against
`orders`.

### `favorites`

Pure many-to-many join between `users` (customers only, by convention) and `restaurants`, unique
per `(customer_id, restaurant_id)` — a customer can favorite many restaurants, and a restaurant can
be favorited by many customers.

### `platform_settings`

A single-row configuration table (no `restaurant_id`/`customer_id` — it is platform-wide, edited
only by `ADMIN` via `AdminSettingsController`). Not part of the relational graph; every other
table is either directly or transitively anchored to `users` or `restaurants`.

## 3. Cardinality Summary

| Relationship | Cardinality |
|---|---|
| User (customer) → Addresses | 1 : N |
| User (owner) → Restaurants | 1 : N (0 or 1 owner per restaurant, nullable) |
| User (customer) → Cart | 1 : 1 |
| User (customer) → Orders | 1 : N |
| User (customer) → Favorites → Restaurants | N : N |
| Restaurant → Menu Categories | 1 : N |
| Restaurant → Menu Items | 1 : N |
| Menu Category → Menu Items | 1 : N |
| Restaurant ↔ Categories (cuisine tags) | N : N |
| Cart → Cart Items → Menu Items | 1 : N, then N : 1 |
| Order → Order Items | 1 : N |
| Order → Revenue Transaction | 1 : 0..1 |
| Restaurant → Orders | 1 : N |
| Restaurant → Revenue Transactions | 1 : N |

## 4. Tables That No Longer Exist

These were introduced and later deliberately dropped by a migration; they are called out here so
this document isn't mistaken for describing a schema that includes them:

| Table | Introduced | Dropped | Why |
|---|---|---|---|
| `coupons`, `coupon_usages` | `V1` | `V8` | Coupon/discount feature removed; `orders.coupon_code` and `orders.discount` were dropped in the same migration. |
| `reviews` | `V5` | `V11` | Review feature removed; `restaurants.rating_average` and `restaurants.review_count` were dropped alongside it. |
| `password_reset_tokens` | `V7` | `V10` | Password-reset-by-email flow removed. |

If you are looking for rating/review or coupon logic anywhere in the current codebase, it does not
exist — these three removals are the reason why.
