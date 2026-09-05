# Phase 7.1 development report — Commerce order foundation

Date: 2026-09-05

Status: implementation and local verification complete; Git delivery evidence is recorded below as it finishes.

## Delivered behavior

- Introduced `commerce` as a Spring Modulith bounded context rather than adding payment concerns to catalog or learning.
- Added paid-course order creation at `POST /api/v1/me/orders` with a mandatory caller-generated UUID `Idempotency-Key`.
- Added bounded learner order history at `GET /api/v1/me/orders?limit=20` with a hard maximum of 100.
- Returns `201 Created` for the winning insert and `200 OK` for a replay of the same learner/key.
- Snapshots course id, slug, title, amount, and currency so a retry does not depend on mutable catalog state.
- Derives `PENDING_PAYMENT` or `EXPIRED` from the configured pending window without claiming a payment occurred.
- Rejects free-course orders and learners who already have access through explicit domain/application policy.

## Architecture and maintainability

- Domain and application code contain no Spring, MVC, JPA, or infrastructure dependencies.
- `commerce` reads catalog, identity, and learning only through named public module interfaces.
- Learning exposes only `EnrollmentAccessLookup`; its enrollment repository and entity remain private.
- The web controller calls `CourseOrderUseCase` and maps API contracts to REST response DTOs.
- Persistence owns a separate `CourseOrderJpaEntity`, Spring Data repository, mapper, and adapter.
- The transaction wrapper keeps Spring transaction mechanics outside the application service.
- No gateway abstraction, payment callback, provider constant, fake payment state, or frontend checkout was created before a provider decision.

## Persistence, concurrency, and performance

- Flyway V11 creates a commerce-owned `course_orders` table with database checks for positive amount, uppercase currency, and expiry ordering.
- Unique `(user_id, idempotency_key)` plus PostgreSQL `ON CONFLICT DO NOTHING` makes concurrent retries converge on one row.
- The learner/key lookup is covered by the unique index; recent history uses `(user_id, created_at DESC, id DESC)`.
- History is bounded before reaching persistence and uses a stable deterministic order.
- Cross-module foreign keys and JPA relationships were intentionally avoided to retain extraction options.

## Security

- Learner identity comes only from the validated JWT subject; request bodies cannot choose an owner.
- Create and history endpoints require authentication under the existing stateless resource-server policy.
- CORS explicitly permits `Idempotency-Key` for the configured first-party origin.
- No access token, learner email, payment credential, provider reference, gateway secret, or card data is stored.
- No browser-controlled endpoint can mark an order paid or grant enrollment.

## Verification

Focused checks completed before the full gate:

- Domain/application/architecture group: 16 tests passed.
- PostgreSQL persistence and concurrent idempotency: 3 tests passed.
- Configuration, ArchUnit, Spring Modulith, and persistence regression group: 9 tests passed.
- MockMvc and Spring Security API contract: 5 tests passed.
- Spring Modulith isolated bootstrap and real HTTP-to-PostgreSQL flow: 2 tests passed.

- Full `mvn verify`: 175 tests passed, 0 failures, 0 errors, 0 skipped.
- Spring Boot executable JAR packaging passed.
- JaCoCo analyzed 214 classes and the 70% bundle line-coverage gate passed.

Feature CI passed for the complete implementation/report commit. Merge-time verification and main CI remain pending. A failed gate blocks push or merge.

## Cohesive commits

- `295289d` — define the ADR and migration plan.
- `ed21559` — expose the learning enrollment-access boundary.
- `99b7f6e` — implement the idempotent commerce domain/application core.
- `b18ed43` — persist order snapshots with database-level idempotency.
- `6a1711b` — configure the transaction boundary and pending TTL.
- `2f8c1a3` — expose the authenticated REST/security contract.
- `b8b68af` — verify module isolation and the real order flow.

## Delivery evidence

- Feature branch: `feature/commerce-order-foundation`
- Feature CI: [run 33959851087](https://github.com/ban2909-personally/ai-learning-api/actions/runs/33959851087), exact head `d4a97bce632d8ce045e9f79c77402529fe77de06`, passed
- Merge commit: pending
- Main CI: pending

## Deferred decision

Slice 7.2 remains blocked by a real payment-provider choice and its operational constraints. This is deliberate: the platform must never infer payment success from the browser or ship a fake production callback.
