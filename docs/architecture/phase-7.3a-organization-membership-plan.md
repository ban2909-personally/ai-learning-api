# Phase 7.3a — Organization and membership authorization plan

## Definition of done

- [x] Close Phase 7.1 local, feature CI, merge, and main CI evidence.
- [x] Inspect identity lookup, JWT ownership, persistence, transaction, Modulith, ArchUnit, and testing seams.
- [x] Record tenant ownership, idempotency, slug, role, privacy, and deferred-capability decisions before code.
- [x] Add framework-free organization, membership, role, and authorization domain logic.
- [x] Add application commands, use cases, output ports, and Mockito tests.
- [x] Add Flyway V12, separate JPA entities, conflict-safe creation, bounded queries, and PostgreSQL tests.
- [x] Add JWT-scoped create/mine/roster endpoints with validation, MockMvc, and Spring Security tests.
- [x] Add Spring Modulith isolated-module coverage and preserve ArchUnit rules.
- [ ] Run complete Maven gates, diff/security audit, feature CI, merge-time gates, and main CI.
- [ ] Publish a development report covering files, architecture, tests, security, performance, commits, and CI evidence.

## Controlled implementation sequence

1. Domain roles, normalized business identifiers, ownership, and authorization policy.
2. Application use case and storage port with idempotent retry semantics.
3. Flyway/JPA persistence with atomic organization-plus-owner creation.
4. Transaction wrapper and module configuration.
5. REST DTO/controller with JWT-only ownership and bounded collections.
6. Unit, persistence concurrency, HTTP/security, Modulith, ArchUnit, and full regression gates.
7. Feature push/CI, local no-fast-forward merge, merge verification, main push/CI.

## Explicitly deferred to Slice 7.3b

- invitations and acceptance tokens;
- adding, removing, suspending, or changing members;
- ownership transfer;
- subscription and seat-count semantics;
- catalog course assignment;
- organization learning analytics;
- organization administration frontend.
