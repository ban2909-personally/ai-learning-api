# ADR-006 — Provider-neutral commerce order foundation

Status: accepted for Phase 7.1

Date: 2026-09-05

## Context

Paid courses currently return `payment_required` from the learning module. The platform needs durable order identity, immutable price snapshots, learner-scoped history, and retry-safe creation before it can integrate a payment provider. Choosing Stripe, VNPay, MoMo, or another gateway changes redirect, signature, settlement, refund, and webhook contracts and has not been decided.

Implementing a fake production gateway or letting the browser confirm payment would create an unsafe path to enrollment. Coupling orders to catalog JPA entities or learning repositories would also violate the modular-monolith boundary and make later service extraction expensive.

## Decision

Create a top-level `commerce` bounded context for Phase 7.1.

- `commerce` may depend only on `catalog::published-course`, `identity::user-lookup`, `learning::enrollment-access`, `platform::security`, and `sharedkernel::error`.
- Catalog supplies a published-course snapshot through its existing public lookup. Commerce stores course id, slug, title, amount, and currency as immutable order data; it never reads catalog tables or entities.
- Learning exposes a narrow `EnrollmentAccessLookup` named interface. Commerce uses it only to reject orders for an already enrolled learner; it never accesses enrollment persistence.
- A required caller-generated UUID `Idempotency-Key` is unique per learner. Repeating the same key returns the original snapshot without re-reading mutable catalog data.
- New paid-course orders are `PENDING_PAYMENT` until their configured expiry. The API may present an elapsed pending order as `EXPIRED`; Phase 7.1 does not mutate it or grant access.
- Free courses remain on the existing direct-enrollment path. Creating a commerce order for a free course fails with a stable conflict error.
- The REST API derives learner identity exclusively from the validated JWT subject and exposes bounded recent history.
- Persistence uses a commerce-owned JPA entity and Spring Data repository inside the outbound adapter. A native conflict-safe insert provides database-level idempotency; there is no empty repository implementation.
- Application/domain code stays framework-free. Spring transactions, MVC, security annotations, JPA, and configuration remain in adapters/configuration.

Phase 7.1 deliberately does not expose a payment callback, mark an order paid, enroll a learner, or render a frontend checkout. Those actions require a real provider contract and verified server-to-server payment evidence.

## API contract

```http
POST /api/v1/me/orders
Authorization: Bearer <access token>
Idempotency-Key: <UUID>
Content-Type: application/json

{"courseSlug":"spring-boot-production"}
```

The first successful creation returns `201 Created`; an idempotent replay returns `200 OK` with the same order id and price snapshot.

```http
GET /api/v1/me/orders?limit=20
Authorization: Bearer <access token>
```

History is ordered by `createdAt DESC, id DESC`, defaults to 20 rows, and is bounded to 100.

## Persistence invariants

- UUID primary key.
- Unique `(user_id, idempotency_key)` constraint.
- Positive amount and uppercase three-letter currency constraints.
- Pending expiry strictly after creation.
- Learner-history index on `(user_id, created_at DESC, id DESC)`.
- No access token, email, payment credential, gateway secret, or mutable catalog relation is stored.

## Consequences

The next payment slice can add a gateway port, provider adapter, signed webhook inbox, and transactional paid-order-to-enrollment handoff without changing order identity or browser trust boundaries. Until then, paid checkout remains unavailable in the UI rather than pretending that an order reservation is a payment.
