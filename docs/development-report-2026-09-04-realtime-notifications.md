# Phase 6.2 development report — Durable realtime notifications

Date: 2026-09-04

Status: implementation and local quality gates complete; feature/main CI delivery is recorded separately by the Phase 6 checklist.

## Delivered outcome

Phase 6.2 turns the versioned `lesson.completed` Kafka event from Phase 6.1 into a durable, per-user notification inbox. WebSocket is used only for low-latency delivery; PostgreSQL history remains the source of truth and the browser catches up over HTTP after startup or reconnect.

The public lesson-progress, catalog, identity, mentoring, and database behavior from earlier phases remains unchanged. Flyway V9 is additive.

## Architecture and conventions

- Added the top-level `notification` Spring Modulith bounded context.
- Its only business-module dependency is the named `learning::events` API. Platform security and shared error contracts remain explicit reusable dependencies.
- Domain and application code contain no Spring, JPA, HTTP, Kafka, STOMP, or JDBC types.
- Inbound adapters own REST, transaction, Kafka, and STOMP authentication concerns.
- Outbound adapters own PostgreSQL and realtime user-destination delivery.
- REST DTOs remain in the web adapter; application contracts are transport-neutral.
- Interfaces exist only for the use-case boundary and the two replaceable outbound boundaries (`NotificationStore` and `NotificationRealtimeDelivery`).
- Notification identity equals the integration-event id, making Kafka redelivery idempotent without a second deduplication table.

## Backend behavior

### Durable projection and HTTP catch-up

- Flyway V9 creates `user_notifications`, a stable history index `(user_id, created_at DESC, id DESC)`, and a partial unread index.
- `INSERT ... ON CONFLICT DO NOTHING` makes duplicate event delivery a successful no-op.
- Keyset pagination is bounded to 1–50 rows and never performs an offset scan.
- `GET /api/v1/me/notifications` reads only the JWT subject's rows.
- `PATCH /api/v1/me/notifications/{notificationId}/read` includes `user_id` in the update predicate and returns the same not-found result for absent and foreign-owned ids.

### Kafka input

- Consumer configuration is disabled by default and independently deployable through environment properties.
- The adapter verifies topic, stable user key, exactly-one envelope headers, content type, event type, schema version, ids, timestamp, and complete payload before invoking the use case.
- Record acknowledgement mode is `RECORD`; the transactional decorator commits PostgreSQL before the listener returns.
- Failures propagate to the container and retry with a validated delay of at least 1 ms; permanent DLT/replay operations remain explicit Phase 6.3 work.
- Low-cardinality Micrometer outcomes distinguish projected, duplicate, and rejected processing attempts.

### WebSocket delivery

- Native STOMP endpoint: `/ws/notifications`; subscription: `/user/queue/notifications`.
- The HTTP upgrade is restricted to the configured exact frontend origin.
- JWT is sent only in the STOMP `CONNECT` native header. It is decoded by the existing issuer/signature/expiry validator and requires a UUID subject.
- Message authorization permits only the authenticated user notification subscription and denies arbitrary subscriptions and every client `SEND` frame.
- The raw WebSocket is registered before `CONNECT` and closed with policy violation at token expiry.
- Message size, send buffer, send time, first-message timeout, heartbeat, and reconnect delay are bounded.
- Realtime publication is registered with transaction synchronization and runs only after commit. Delivery failure is measured and cannot roll back the durable row.
- The dedicated WebSocket scheduler is not a default application scheduling candidate, avoiding contention with business jobs.

## Frontend behavior

- Added `@stomp/stompjs` with a pinned lockfile entry.
- A single notification provider owns HTTP history, reconnect catch-up, STOMP lifecycle, deduplication, unread count, and read transitions.
- Access tokens remain in memory. STOMP `CONNECT` obtains a fresh-enough token through a single-flight refresh-cookie flow; tokens never appear in URLs or local storage.
- Concurrent refresh consumers share one request, and session epochs prevent an old in-flight refresh from restoring a logged-out or replaced session.
- Incoming payloads are allowlisted to known event types and application-relative paths; protocol-relative navigation is rejected.
- The header notification menu has an accessible dialog, unread announcement, keyboard Escape handling, outside-click dismissal, connection status, retry state, bounded scrolling, and keyset “load more”.
- Mobile labels and menu width adapt without horizontal overflow.

## Changed-file manifest

### API repository

- Configuration and dependencies: `.env.example`, `pom.xml`, `src/main/resources/application.yml`.
- Architecture documentation: `docs/architecture/ADR-004-idempotent-realtime-notifications.md`, `docs/architecture/phase-6-realtime-learning-events-plan.md`.
- Module root: `src/main/java/com/ailearning/platform/notification/**`.
- Platform integration: `SecurityConfig`, `GlobalExceptionHandler`, and its unit test.
- Database: `src/main/resources/db/migration/V9__create_user_notifications.sql`.
- Tests: `src/test/java/com/ailearning/platform/notification/**` plus notification SQL fixtures.

### Web repository

- Dependency manifest: `package.json`, `pnpm-lock.yaml`.
- Composition/header: `src/app/App.tsx`, `src/components/AppHeader.tsx`.
- Session lifecycle: `src/features/auth/AuthContext.tsx` and test.
- Notification UI/state: `src/features/notifications/**`, `src/types/notification.ts`.
- URL boundary: `src/lib/api.ts` and test.
- Existing learning component mocks were updated only for the added auth-context token function.

The authoritative exact manifests are reproducible with:

```text
git -C E:\ai-learning-api diff --name-status 70c85c8..feature/realtime-notifications
git -C E:\ai-learning-web diff --name-status c73f16a..feature/realtime-notifications
```

## Verification evidence

### API

- `mvn clean verify`
- 130 tests across 50 suites; 0 failures, 0 errors, 0 skipped.
- Spring Modulith verification passed.
- ArchUnit dependency-direction rules passed.
- PostgreSQL, Kafka, Redis, and MinIO Testcontainers tests passed.
- Flyway validated and applied all nine migrations in clean PostgreSQL containers.
- JaCoCo line coverage: 87.01% (1,601 covered, 239 missed) across 177 analyzed classes; the 70% gate passed.

### Web

- `pnpm test`: 14 files, 19 tests, all passed.
- `pnpm lint`: TypeScript project check passed.
- `pnpm build`: production bundle passed; main JavaScript gzip size 92.42 kB.
- `pnpm test:e2e`: Selenium smoke passed.
- Browser verification at 320 px, 768 px, and 1440 px found no horizontal overflow and a stable header height.

## Security and performance audit

- No secret, refresh token, access token, email, course title, or lesson title is written to Kafka payload logs or notification content.
- Kafka validation fails closed on ambiguous/mismatched envelope data.
- Cross-user HTTP reads/updates and arbitrary STOMP destinations are denied.
- Exact Origin and JWT-in-CONNECT protect the public upgrade endpoint without cookie-authenticated STOMP CSRF exposure.
- SQL predicates and indexes are user-first; result sizes and in-memory browser history are bounded.
- Kafka counters and realtime delivery counters use fixed low-cardinality tags.
- No query-string credentials, local-storage token, unbounded list, empty repository implementation, cross-module entity access, or framework dependency in domain/application was introduced.

## Deliberate Phase 6.3 follow-up

- Add reviewed dead-letter retention, alerting, replay authorization, and runbooks.
- Add analytics projections and lag/backlog thresholds.
- Load-test consumer lag, STOMP backpressure, and ordering.
- Replace the local simple broker with a broker relay or notification gateway before multi-instance realtime fan-out.
