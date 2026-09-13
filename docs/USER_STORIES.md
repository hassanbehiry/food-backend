# User Stories

This document restates `USE_CASES.md` as user stories with acceptance criteria, in the same format
as `DELIVERY_WORKFLOW.md` — tables mapping each story to the endpoint(s) and status/entity fields
that satisfy it, plus a closing section on assumptions this adaptation makes explicit. Every story
is traceable to a real controller under `src/main/java/com/food/foodapp/**/controller`; none
describe unbuilt functionality.

## 1. Guest Stories

| # | Story | Endpoint | Acceptance criteria |
|---|---|---|---|
| G1 | As a guest, I want to browse restaurants without logging in, so that I can decide where to order from before creating an account. | `GET /api/v1/restaurants` | Only restaurants with `approval_status = APPROVED` appear; results can be filtered by `category` slug or free-text search. |
| G2 | As a guest, I want to view a restaurant's full menu, so that I can see prices and dishes before signing up. | `GET /api/v1/restaurants/{id}/menu` | Menu is grouped by `menu_categories.display_order`; unavailable items (`available = false`) are visually distinguishable, not hidden. |
| G3 | As a guest, I want to register as a customer or a restaurant owner, so that I can start ordering or start selling. | `POST /api/v1/auth/register` | Duplicate email → `409`; a new `OWNER` registration is blocked with `RestaurantRegistrationClosedException` if `platform_settings.allow_restaurant_registration = false`. |
| G4 | As a returning user, I want to log in and stay logged in across page reloads, so that I don't re-authenticate constantly. | `POST /api/v1/auth/login`, `GET /api/v1/auth/me` | A JWT is set as an HTTP-only cookie; a suspended account (`users.status = SUSPENDED`) is rejected with a clear error, not a generic 401. |

## 2. Customer Stories

### Cart & Checkout

| # | Story | Endpoint | Acceptance criteria |
|---|---|---|---|
| C1 | As a customer, I want to add items from a restaurant to my cart, so that I can build an order before paying. | `POST /api/v1/cart/items` | Adding an item from a second restaurant while the cart already holds items from a first is rejected (`CartRestaurantConflictException`), not silently mixed. |
| C2 | As a customer, I want to adjust quantities or remove items in my cart, so that I can fix mistakes before checkout. | `PATCH /api/v1/cart/items/{cartItemId}`, `DELETE /api/v1/cart/items/{cartItemId}` | Quantity is clamped `1–50`; removing the last item leaves an empty (not deleted) cart row. |
| C3 | As a customer, I want my cart to survive logging out and back in on another device, so that I don't lose my selection. | `POST /api/v1/cart/sync` | A client-cached cart merges into the server cart keyed by `menu_item_id`; server prices always win over anything the client sends. |
| C4 | As a customer, I want to check out with a delivery address and pay on delivery, so that I can complete my order. | `POST /api/v1/cart/checkout` | `subtotal`/`delivery_fee`/`total` are computed server-side from live menu prices; an empty cart is rejected (`CartEmptyException`); the cart is cleared only after the order is successfully created. |

### Order Lifecycle (Customer Side)

| # | Story | Order status touched | Endpoint |
|---|---|---|---|
| C5 | As a customer, I want to see my past and current orders in one place, so that I can track my spending and reorder favorites. | any | `GET /api/v1/orders` |
| C6 | As a customer, I want a step-by-step tracker for an active order, so that I know whether it's being cooked or already on the way. | `CONFIRMED → PREPARING → READY_FOR_DELIVERY → OUT_FOR_DELIVERY → DELIVERED` | `GET /api/v1/orders/{orderId}/track` |
| C7 | As a customer, I want to confirm that my order arrived, so that the order closes out on my side even if the restaurant forgets to. | `OUT_FOR_DELIVERY → DELIVERED` | `PUT /api/v1/orders/{orderId}/confirm-delivery` |
| C8 | As a customer, I want to cancel an order before it's dispatched, so that I'm not charged for something I no longer want. | `CONFIRMED / PREPARING / READY_FOR_DELIVERY → CANCELLED` | `POST /api/v1/orders/{orderId}/cancel` |

**Acceptance criteria for C7/C8 shared by both:** attempting either after the order is already
`OUT_FOR_DELIVERY` (for C8) or already `DELIVERED`/`CANCELLED` (for both) returns `409 Conflict` —
never a silent no-op or a `500`. A mismatched customer requesting someone else's order gets `404`,
not `403`, so an order's existence is never leaked to a non-owner.

### Account Management

| # | Story | Endpoint | Acceptance criteria |
|---|---|---|---|
| C9 | As a customer, I want to save multiple delivery addresses and mark one as default, so that checkout is fast. | `AddressController` (`GET`/`POST`/`PUT`/`PATCH .../default`/`DELETE`) | Setting a new default unsets the previous one; deleting the sole address is allowed (no "must have one" constraint). |
| C10 | As a customer, I want to favorite restaurants I like, so that I can find them again quickly. | `POST /api/v1/user/favorites/toggle` | Toggling twice returns to the original state; the pair `(customer_id, restaurant_id)` is unique — no duplicate favorites. |
| C11 | As any signed-in user, I want to update my name, phone, and avatar, so that my profile stays accurate. | `PUT /api/v1/user/profile` | Email is not editable through this endpoint (it is the login identifier); partial updates don't null out untouched fields. |

## 3. Restaurant Owner Stories

### Setup & Menu Management

| # | Story | Endpoint | Acceptance criteria |
|---|---|---|---|
| O1 | As a restaurant owner, I want to set my delivery fee, minimum order, and opening hours, so that customers see accurate information. | `PUT /api/v1/owner/restaurants/{restaurantId}/settings` | A non-owner caller gets `403`; `close_time` must be after `open_time` when both are set (DB `CHECK`). |
| O2 | As a restaurant owner, I want to organize my menu into categories and reorder them, so that customers see my menu the way I intend. | `OwnerMenuCategoryController` (`POST`, `PUT /{categoryId}`, `PUT /reorder`, `DELETE /{categoryId}`) | Category names are unique per restaurant (`DuplicateMenuCategoryException` on collision); reordering is atomic — either every position updates or none do. |
| O3 | As a restaurant owner, I want to add, edit, and remove menu items, and toggle their availability, so that I never sell something I'm out of. | `OwnerMenuItemController` (`POST`, `PUT /{itemId}`, `DELETE /{itemId}`, `PATCH /{itemId}/availability`) | An unavailable item cannot be added to a customer's cart (`MenuItemUnavailableException`); price changes never retroactively affect already-placed orders (order items are snapshotted, see `DATABASE_SCHEMA.md` §2). |

### Order Fulfillment

| # | Story | Status transition | Endpoint |
|---|---|---|---|
| O4 | As a restaurant owner, I want to see new orders the moment a customer places them, so that my kitchen can start immediately. | (order enters at `CONFIRMED`) | `GET /api/v1/owner/restaurants/{restaurantId}/orders` |
| O5 | As a restaurant owner, I want to mark an order as being prepared and then ready, so that my staff and the dashboard reflect kitchen progress. | `CONFIRMED → PREPARING → READY_FOR_DELIVERY` | `PATCH .../orders/{orderId}/status` |
| O6 | As a restaurant owner, I want to hand an order off to a courier and optionally record their name, so that I have a record of who took it. | `READY_FOR_DELIVERY → OUT_FOR_DELIVERY` | `POST .../orders/{orderId}/send-to-delivery` |
| O7 | As a restaurant owner, I want to mark an order delivered myself if the customer never confirms it, so that the order (and my revenue) doesn't stay stuck open forever. | `OUT_FOR_DELIVERY → DELIVERED` | `POST .../orders/{orderId}/deliver` |
| O8 | As a restaurant owner, I want a single dashboard view of everything ready-to-ship or already out, so that I don't have to page through full order history to manage active deliveries. | (no transition — read view) | `GET .../orders/delivery-dashboard` |

**Acceptance criteria for O7 (the story with the highest correctness stakes):** the status flip and
the `revenue_transactions` write happen in one database transaction — a `200` response always means
both committed (`DELIVERY_WORKFLOW.md` §4). If the customer's own confirmation (C7) reaches the
order first, O7 is rejected with `409`, and vice versa — a `PESSIMISTIC_WRITE` row lock plus the
`orders.status` state machine plus a `UNIQUE(order_id)` constraint on `revenue_transactions` make
double-counting the same order's revenue structurally impossible, not just unlikely.

### Insight

| # | Story | Endpoint |
|---|---|---|
| O9 | As a restaurant owner, I want to see my revenue and order volume over time, so that I can judge how my restaurant is doing. | `GET .../analytics/overview`, `GET .../analytics/revenue` |
| O10 | As a restaurant owner, I want a quick-glance dashboard when I log in, so that I don't have to hunt for today's numbers. | `GET /api/v1/owner/dashboard/{restaurantId}` |

**Acceptance criteria:** every figure is scoped strictly to restaurants the caller owns; requesting
another owner's `restaurantId` returns `403`/`404` per the ownership guard, never another owner's
numbers.

## 4. Admin Stories

| # | Story | Endpoint | Acceptance criteria |
|---|---|---|---|
| A1 | As an admin, I want to review and approve newly registered restaurants, so that only legitimate businesses appear to customers. | `PATCH /api/v1/admin/restaurants/{id}/approve` | A `PENDING` restaurant becomes invisible-to-public until `APPROVED`; approving an already-`APPROVED` restaurant is a no-op transition rejected as invalid. |
| A2 | As an admin, I want to reject or suspend a restaurant, so that I can act on policy violations or bad actors. | `PATCH .../{id}/reject`, `PATCH .../{id}/suspend` | A `SUSPENDED` restaurant immediately disappears from public listing/menu endpoints without deleting its data (owner's menu/orders remain intact for records). |
| A3 | As an admin, I want to suspend or reactivate a user account, so that I can respond to abuse without deleting their history. | `PATCH /api/v1/admin/users/{id}/status` | An admin cannot suspend themselves or another admin (`AdminActionForbiddenException`); a suspended user's next login attempt is rejected. |
| A4 | As an admin, I want to toggle maintenance mode and control whether new restaurants can register, so that I can manage the platform during incidents or growth phases. | `PUT /api/v1/admin/settings` | `maintenance_mode = true` blocks non-admin write requests platform-wide; `allow_restaurant_registration = false` blocks new `OWNER` sign-ups that would create a restaurant. |
| A5 | As an admin, I want platform-wide order, revenue, and geographic dashboards, so that I can see overall business health, not just one restaurant's. | `GET /api/v1/admin/analytics/{overview,orders,revenue,orders-by-city}` | Figures aggregate across every restaurant regardless of individual owner; date-range filters apply consistently across all four endpoints. |

## 5. Design Decisions & Assumptions in This Adaptation

Adapting `DELIVERY_WORKFLOW.md`'s format to user stories required a few judgment calls, called out
here the same way that document calls out its own deviations:

1. **No separate "Courier" persona.** The system has exactly three roles
   (`CUSTOMER`/`OWNER`/`ADMIN` — `users.role` `CHECK` constraint); a courier is not an account, so no
   courier-facing stories are written. `deliveryPersonName` stories are folded into the Owner's
   dispatch story (O6) as a field they optionally fill in, not a separate actor's workflow.
2. **Guest stories are intentionally minimal.** Only browsing and registration are unauthenticated;
   every other capability (cart, orders, favorites, addresses) requires an account, so those stories
   are written under Customer, not Guest, even though a guest could reach the login wall while
   attempting them.
3. **One story can span multiple endpoints when they're the same user intent.** E.g. C9 (manage
   addresses) covers five HTTP methods on one resource rather than five near-duplicate stories,
   matching how `DELIVERY_WORKFLOW.md` groups related endpoints under one workflow section rather
   than one row per method.
4. **Acceptance criteria cite the actual exception types and constraints** (e.g.
   `CartRestaurantConflictException`, the `revenue_transactions` unique constraint) rather than
   generic "should show an error," so a story can be verified directly against existing code and
   tests instead of re-interpreted at implementation time.
5. **No stories for removed features.** Coupons and reviews existed in earlier migrations
   (`V1`/`V5`) and were deliberately dropped (`V8`/`V11` — see `DATABASE_SCHEMA.md` §4); this
   document reflects the product as it stands today and does not include stories for functionality
   that was built and then intentionally retired.
