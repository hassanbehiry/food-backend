# Order, Delivery & Revenue Workflow

This document describes the order lifecycle, delivery dispatch/confirmation flow, and revenue
recognition logic implemented across the `order` package (`entity`, `service`, `controller`, `dto`,
`mapper`, `repository`) and Flyway migration `V14`.

It extends the existing order system rather than replacing it — see "Design decisions & deviations"
at the end for exactly what was adapted from the original feature request to fit this codebase's
existing conventions and history.

## 1. Order Lifecycle & State Machine

```
CONFIRMED ──▶ PREPARING ──▶ READY_FOR_DELIVERY ──▶ OUT_FOR_DELIVERY ──▶ DELIVERED
    │              │                 │
    └──────────────┴─────────────────┴──▶ CANCELLED
```

| Status | Meaning | Set by |
|---|---|---|
| `CONFIRMED` | Order placed; immediately visible to the restaurant. Placing an order *is* the customer's confirmation — there is no separate pending/unconfirmed row. | `POST /api/v1/orders` |
| `PREPARING` | Kitchen is working on the order. | Owner, via `PATCH .../orders/{id}/status` |
| `READY_FOR_DELIVERY` | Food is ready for courier pickup. | Owner, via `PATCH .../orders/{id}/status` |
| `OUT_FOR_DELIVERY` | Handed to a courier. `sentToDeliveryAt` and (optionally) `deliveryPersonName` are stamped here. | Owner, via `POST .../orders/{id}/send-to-delivery` |
| `DELIVERED` | Terminal. `deliveredAt` and `deliveredBy` (`CUSTOMER` or `OWNER`) are stamped here. **Revenue is recognized in the same transaction.** | Customer (`PUT /api/v1/orders/{id}/confirm-delivery`) **or** Owner (`POST .../orders/{id}/deliver`) — whichever happens first |
| `CANCELLED` | Terminal. Called off before dispatch. | Customer or owner, from `CONFIRMED`/`PREPARING`/`READY_FOR_DELIVERY` only |

The single source of truth for legal transitions is `OrderStatus.canTransitionTo(...)`
(`order/entity/OrderStatus.java`), a static table every status-changing method in `OrderService`
routes through. An illegal transition — including re-processing an already-`DELIVERED` or
`CANCELLED` order — always throws `InvalidOrderStatusTransitionException`, mapped by
`GlobalExceptionHandler` to **HTTP 409 Conflict**.

### Two independent paths to `DELIVERED`

Unlike the literal spec (a single restaurant/delivery-driven "Delivered" action), this
implementation keeps the **pre-existing customer confirmation** (`confirmDelivery`) *and* adds the
new **restaurant/delivery confirmation** (`deliverOrder`) side by side:

- Both require the order to currently be `OUT_FOR_DELIVERY`.
- Both write `status`, `deliveredAt`, and `deliveredBy` in one `@Transactional` method.
- Both are backed by a `PESSIMISTIC_WRITE` row lock (`OrderRepository.findByIdAndCustomerIdForUpdate`
  / `findByIdAndRestaurantIdForUpdate`), so whichever call reaches the row first wins and the other
  is rejected as an illegal transition (`409`) — there is no way for both to succeed against the
  same order, and therefore no way to double-count its revenue.

This was a deliberate choice to preserve an existing feature (the customer confirming their own
receipt) while adding the one the task requested — see "Design decisions & deviations" below.

## 2. API Endpoint Specs

All endpoints are under `/api/v1`. Owner endpoints require authentication and restaurant ownership
(`RestaurantOwnershipGuard` — non-owner callers get `403`); customer endpoints require the caller to
own the order (a mismatched customer gets `404`, not `403`, so an order's existence is never leaked
to a non-owning caller).

| Method & Path | Caller | Effect |
|---|---|---|
| `POST /orders` | Customer | Creates an order, `status=CONFIRMED` |
| `PATCH /owner/restaurants/{restaurantId}/orders/{orderId}/status` | Owner | `status: PREPARING \| READY_FOR_DELIVERY \| CANCELLED` |
| `POST /owner/restaurants/{restaurantId}/orders/{orderId}/send-to-delivery` | Owner | `READY_FOR_DELIVERY → OUT_FOR_DELIVERY`. Body (optional): `{ "deliveryPersonName": "..." }` |
| `POST /owner/restaurants/{restaurantId}/orders/{orderId}/deliver` | Owner | `OUT_FOR_DELIVERY → DELIVERED` + revenue recognition (atomic) |
| `PUT /orders/{orderId}/confirm-delivery` | Customer | `OUT_FOR_DELIVERY → DELIVERED` (no revenue side-effect written here — see §4) |
| `GET /owner/restaurants/{restaurantId}/orders/delivery-dashboard` | Owner | Delivery Orders section: summary + active queue |
| `POST /orders/{orderId}/cancel` | Customer | `→ CANCELLED` (only while `CONFIRMED`/`PREPARING`/`READY_FOR_DELIVERY`) |

### `POST .../deliver` response

```json
{
  "order": {
    "id": 1025,
    "orderNumber": "ORD-20260912-000042",
    "status": "DELIVERED",
    "total": 250.00,
    "deliveredAt": "2026-09-12T23:36:41",
    "deliveredBy": "OWNER",
    "sentToDeliveryAt": "2026-09-12T23:10:00",
    "deliveryPersonName": "Mohamed",
    "...": "full OwnerOrderResponse fields"
  },
  "revenue": {
    "amount": 250.00,
    "createdAt": "2026-09-12T23:36:41"
  }
}
```

A `200` response here always means both the status change and the revenue row committed — there is
no partial-success shape (see §4).

### Error responses

| Status | Cause |
|---|---|
| `400` | Malformed body / unparseable `status` value (e.g. `status: "SHIPPED"`) |
| `403` | Authenticated caller does not own the restaurant |
| `404` | Order/restaurant does not exist, or exists but isn't the caller's own |
| `409` | Illegal state transition — including "not yet `OUT_FOR_DELIVERY`" and "already `DELIVERED`" (duplicate-click protection) |

*(Note on 400 vs 409: the existing codebase already maps every illegal `OrderStatus` transition to
`409` uniformly via one exception type, rather than splitting "wrong status generically" (400) from
"already terminal" (409). This implementation follows that existing convention instead of
introducing a parallel 400 path — see "Design decisions & deviations".)*

## 3. Database Changes & Constraints

**Migration `V14__add_kitchen_prep_and_delivery_tracking.sql`** — purely additive, no data loss:

- `orders.status` check constraint widened to add `PREPARING`, `READY_FOR_DELIVERY` (existing rows
  are untouched — every one is already a valid value in the new, wider set).
- `orders.sent_to_delivery_at timestamp` (nullable) — stamped by `send-to-delivery`.
- `orders.delivery_person_name varchar(150)` (nullable) — optional courier label; there is no
  courier/driver account or role in this system (`Role` is `CUSTOMER | OWNER | ADMIN`), so this is a
  plain free-text field rather than a relation.
- `orders.delivered_by varchar(20)` (nullable, checked `CUSTOMER`/`OWNER`) — which side confirmed
  delivery.
- New table `revenue_transactions`:

  | Column | Type | Notes |
  |---|---|---|
  | `id` | `bigserial` | PK |
  | `order_id` | `bigint` | **`UNIQUE`**, `NOT NULL`, `REFERENCES orders(id)` |
  | `restaurant_id` | `bigint` | `NOT NULL`, `REFERENCES restaurants(id)` |
  | `amount` | `numeric(10,2)` | `NOT NULL`, `CHECK (amount >= 0)` |
  | `type` | `varchar(30)` | `NOT NULL DEFAULT 'ORDER_PAYMENT'` |
  | `created_at` | `timestamp` | `NOT NULL DEFAULT now()` |

  The `UNIQUE(order_id)` constraint is the hard, database-enforced backstop against ever recording
  an order's revenue twice — verified against a real PostgreSQL instance in
  `RevenueTransactionRepositoryTest` (a second insert for the same `order_id` fails with SQLState
  `23505`, translated by Spring Data JPA into `DataIntegrityViolationException`).

No existing table, column, or row was dropped or altered destructively.

## 4. Revenue Ledger Logic

**The crucial rule — revenue is realized only at `DELIVERED`, never at creation, confirmation, or
dispatch — was already true in this codebase before this change**: `OrderAnalyticsService` computes
every revenue figure (dashboard KPI, revenue chart, admin analytics) as a live
`SUM(total) WHERE status = DELIVERED`, scoped by restaurant/date-range. That computation is
untouched by this work and remains the source of truth for all existing dashboards/analytics.

This change adds an **auditable ledger** alongside that live computation, because it did not exist
before:

- `OrderService.deliverOrder` (the restaurant/delivery-side confirmation) writes one
  `RevenueTransaction` row — `orderId`, `restaurantId`, `amount = order.total`,
  `type = ORDER_PAYMENT` — in the **same `@Transactional` method** as the `DELIVERED` status flip.
  Spring's transaction management means both writes commit together or both roll back together; a
  failure writing the revenue row (including the database rejecting a duplicate `order_id`) rolls
  the status change back too.
- The customer's own confirmation path (`confirmDelivery`) does **not** write a ledger row — it only
  flips the status (as it always has). Revenue is still recognized correctly on that path too,
  because `OrderAnalyticsService`'s live `SUM` reads `orders.status` directly, not the ledger.
- **Idempotency / anti-duplicate protection**, three layers deep:
  1. The `OrderStatus` transition table: `DELIVERED` has no outgoing transitions, so a second
     attempt (from either path) is rejected before any write.
  2. A `PESSIMISTIC_WRITE` row lock on the order, taken by both `deliverOrder` and
     `confirmDelivery`, which serializes concurrent attempts from the two paths against the same
     order.
  3. The database's `UNIQUE(order_id)` constraint on `revenue_transactions`, as defense-in-depth
     behind the first two — `OrderService.recordRevenue` catches a
     `DataIntegrityViolationException` here and translates it into the same `409 Conflict` an
     application-level duplicate would produce, rather than a raw `500`.
- **Backend-only calculation**: the frontend never computes or stores a total/amount for delivery —
  `deliverOrder`'s response echoes back `order.total` (already server-computed at checkout) and the
  `RevenueTransaction`'s persisted `amount`/`createdAt`. No revenue figure is ever accepted from a
  request body.

## 5. Design decisions & deviations from the literal task description

Per the task's own instruction to inspect the existing system first and adopt its conventions
rather than build a parallel one, a few points in the original request were adapted:

1. **Kitchen statuses (`PREPARING`, `READY_FOR_DELIVERY`) were reintroduced.** A prior migration
   (`V12`) had deliberately collapsed `NEW`/`PREPARING`/`ON_THE_WAY` into a single `CONFIRMED`
   status. This change reintroduces `PREPARING`/`READY_FOR_DELIVERY` as new, additive states to
   support the requested kitchen workflow — a real gap the dashboard didn't cover before.
2. **No separate customer-side "PENDING → confirm" step was added.** In this codebase, placing an
   order *is* the confirmation (`CONFIRMED` is the initial status) — there's no cart-side unconfirmed
   row today, and introducing one would be a customer-facing checkout behavior change beyond the
   scope of this task. `PENDING` in the task's spec maps to "cart not yet checked out," which already
   exists.
3. **`OUT_FOR_DELIVERY` is kept as the name for the task's "WITH_DELIVERY"** — it's the existing
   status name used throughout the codebase, frontend, and tests; renaming it everywhere would be
   pure churn with no behavior change.
4. **Delivery confirmation is additive, not a replacement.** The task describes a single
   restaurant-driven "Delivered" action. This implementation keeps the pre-existing
   customer-driven `confirmDelivery` *and* adds the new owner-driven `deliverOrder` alongside it
   (§1), since the customer's own confirmation was an existing feature that doesn't conflict with
   the new requirement — it's a second legal way to reach the same terminal state, safely arbitrated
   by the same state machine and row lock.
5. **No `{success, message, ...}` response envelope.** Every existing endpoint in this codebase
   returns its resource directly (HTTP status codes carry success/failure semantics, and
   `GlobalExceptionHandler` + `ErrorResponse` carry error detail) — there is no precedent for a
   wrapper envelope anywhere in the API. The new endpoints follow that convention; `POST .../deliver`
   nests `order` + `revenue` in one response body (§2) without an added `success`/`message` layer.
6. **400 vs 409 split not introduced.** See the note under §2 — the existing
   `InvalidOrderStatusTransitionException → 409` convention already covers every illegal transition
   uniformly; this change follows it rather than forking a new 400 path for just the new endpoints.
7. **No dedicated courier/driver entity.** There is no delivery-driver account or role in this
   system. `deliveryPersonName` is a plain, optional free-text column on `orders`, per the task's own
   fallback guidance ("make it optional/nullable if no delivery model exists").
