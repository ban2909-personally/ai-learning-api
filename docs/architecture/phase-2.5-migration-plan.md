# Phase 2.5 — Architecture Hardening & Modularization

## Definition of done

- [x] Preserve current REST paths, status codes, JSON fields, security behavior, SQL schema, and
  enrollment idempotency.
- [x] Preserve all uncommitted Phase 3 work.
- [x] Record architectural decisions and package conventions.
- [x] Add Spring Modulith and verify application-module boundaries.
- [x] Add ArchUnit rules for domain purity, adapter isolation, and dependency direction.
- [x] Migrate `identity` to domain/application/ports/adapters.
- [x] Migrate `catalog` without importing Identity domain or persistence types.
- [x] Migrate `learning` without importing Catalog/Identity internals.
- [x] Move reusable technical concerns from `shared` to focused `platform` packages.
- [x] Keep `sharedkernel` minimal: only framework-free error and pagination semantics shared by
  multiple modules.
- [x] Keep Flyway as schema owner and Hibernate at `ddl-auto=validate`.
- [x] Pass unit, Mockito, web/security, PostgreSQL/Flyway, Spring Modulith module, architecture,
  and full regression tests.

## Verification snapshot (2026-08-30)

- `mvn verify`: 23 tests, 0 failures, 0 errors, 0 skipped; executable Spring Boot JAR packaged.
- Spring Modulith boundary verification: pass.
- ArchUnit domain purity, application direction, inbound isolation, and JPA placement: pass.
- PostgreSQL 17 Testcontainers: Flyway V1-V3, Hibernate schema validation, MockMvc/security, catalog,
  identity, and enrollment flows pass.

## Controlled migration sequence

1. Capture the Git diff and baseline test result.
2. Introduce documentation and build-time guardrails.
3. Refactor one bounded context at a time, compiling and testing after each context.
4. Replace cross-module entity/repository access with explicit module APIs or local output ports.
5. Consolidate platform concerns only after business modules no longer depend on the old shared
   packages.
6. Delete superseded packages only after equivalent tests pass.
7. Review the final dependency graph, security configuration, migrations, and complete Git diff.

## Extraction convention

A future microservice takes one top-level business module, its owned tables/migrations, and adapters.
Calls to another module's API become HTTP/RPC clients or event consumers without changing the
calling application service. Platform code is supplied through focused starters; it does not own
business models or policies.
