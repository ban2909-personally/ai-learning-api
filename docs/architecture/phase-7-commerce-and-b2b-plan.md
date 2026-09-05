# Phase 7 — Payments and B2B operations plan

## Outcome

Phase 7 completes the paid-course journey with server-verified payment and then introduces organization-owned seats and reporting without mixing commerce, identity, learning, or catalog persistence.

## Delivery slices

### Slice 7.1 — Provider-neutral order foundation

- Add the `commerce` bounded context and immutable course-price snapshot.
- Create paid-course order intents with learner-scoped UUID idempotency.
- Reject free courses and already enrolled learners through public module contracts.
- Expose bounded authenticated order history.
- Keep payment confirmation and enrollment impossible until a real gateway is selected.

### Slice 7.2 — Payment gateway and enrollment handoff

- Select one provider and record redirect, webhook, signing, refund, settlement, sandbox, and production constraints in a provider-specific ADR.
- Create payment sessions only on the server and bind provider references to an existing order.
- Verify raw webhook signatures before parsing business data.
- Persist webhook receipt idempotently before order transition.
- Mark an order paid and activate enrollment exactly once through an explicit learning-module command.
- Add reconciliation, timeout, failure, refund, and operational metrics.
- Deliver the responsive checkout/result/history UI only against the real provider sandbox contract.

### Slice 7.3 — B2B organization foundation

- Add organization, membership, role, and seat-allocation boundaries.
- Keep platform identity separate from organization membership/authorization.
- Define owner/admin/member permissions and audit events before invitation flows.
- Add organization-scoped course assignment and bounded reporting.

## Slice 7.1 checklist

- [x] Confirm Phase 6.3 backend and Phase 6.4 frontend local/feature/main CI delivery.
- [x] Read the original project discussion and preserve Phase 7 Payments/B2B and Phase 8 Production Readiness ordering.
- [x] Survey catalog price, free enrollment, identity lookup, persistence, transaction, security, module, and test seams.
- [x] Record order ownership, idempotency, price snapshot, expiry, module dependency, and no-fake-payment decisions before code.
- [x] Add a narrow public enrollment-access lookup without exposing learning persistence.
- [x] Add commerce domain invariants, application ports/use cases, and Mockito unit tests.
- [x] Add Flyway V11 order schema, JPA adapter, conflict-safe idempotency, indexes, and PostgreSQL Testcontainers coverage.
- [x] Add JWT-subject-scoped create/history HTTP contracts with validation and Spring Security tests.
- [x] Add Spring Modulith isolation and preserve ArchUnit dependency direction.
- [ ] Run complete Maven gates, security/diff audit, feature CI, merge-time main gates, and main CI.
- [x] Publish a Phase 7.1 development report with files, architecture, tests, security, performance, commits, and CI evidence.

## Provider decision required before Slice 7.2

The selected provider must be named before gateway code is written. The decision must include target market/currency, business/legal account readiness, redirect versus embedded checkout, webhook availability, refund requirements, sandbox credentials, and expected deployment region. No secret is placed in Git or chat.

## Phase 8 boundary

Production deployment, container hardening, supply-chain/security scanning, backup/restore drills, SLOs, staged rollout, and cloud topology remain Phase 8. Phase 7 may add feature-specific telemetry and runbooks but does not claim production readiness.
