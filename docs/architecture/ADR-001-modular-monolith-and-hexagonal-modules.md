# ADR-001: Modular monolith with hexagonal business modules

- Status: Accepted
- Date: 2026-08-30

## Context

The backend currently groups code by business capability (`identity`, `catalog`, `learning`) but lets
application services import JPA repositories, HTTP DTOs, and entities from other modules. Those
dependencies make business logic framework-aware and turn database relationships into module APIs.

We need one deployable application now while retaining an explicit path to independently deployable
services later. Existing HTTP contracts, Flyway migrations, and database behavior must remain stable.

## Decision

The application is a Spring Modulith modular monolith. The direct subpackages of
`com.ailearning.platform` are application modules:

- `identity`: accounts, roles, authentication, and refresh sessions.
- `catalog`: categories and published course discovery.
- `learning`: enrollment and the learner experience.
- `platform`: technical configuration and cross-cutting adapters.
- `sharedkernel`: deliberately small, framework-free primitives only when at least two bounded
  contexts share their semantics.

Each business module uses ports and adapters. Packages are created only when code actually exists:

```text
<module>/
  api/usecase           stable callable module boundary
  api/contract          boundary commands/results
  api/event             published integration events
  domain/...            framework-free business model and rules
  application/port/out  required external capabilities
  application/service/impl
  adapter/in/web/...
  adapter/out/persistence/jpa/...
  config
```

Empty cache, Kafka, MinIO, event, mapper, value-object, or policy packages are not scaffolded.

### Dependency rules

1. Domain code depends only on the JDK and its own module's domain.
2. Application code depends on its domain, API contracts/use cases, and output ports.
3. Inbound web adapters depend on use cases and API contracts; they never call persistence.
4. Outbound adapters implement output ports and own framework-specific entities/repositories.
5. A module may call only another module's named API. It may not import that module's domain,
   application implementation, adapter, repository, or persistence entity.
6. REST DTOs stay in the inbound web adapter. Application services return boundary contracts or
   domain results, never REST response types.
7. JPA entities are persistence models, not domain models. Mapping happens at the adapter boundary.
8. Spring Data interfaces are used directly; custom behavior is implemented by a meaningful
   persistence adapter or repository fragment, never an empty `Impl` class.

Spring Modulith verifies module cycles and allowed named interfaces. ArchUnit verifies internal
layer direction and the absence of Spring/JPA/HTTP dependencies in domain packages.

## Reusable platform base

Technical concerns move under `platform` by capability: `configuration`, `security`, `web.error`,
`observability`, and `persistence.auditing`. Only packages with current behavior are created.
Business concepts such as User, Course, and Enrollment never enter the platform or shared kernel.
This layout can later be extracted into `spring-platform-starters`; the application skeleton and
architecture tests can become a `spring-boot-enterprise-template` without moving business rules.

## Consequences

- More explicit mapping exists at persistence and web boundaries.
- Cross-module joins are adapter concerns and must not leak managed entities across contexts.
- Module APIs and integration events become the seams for future service extraction.
- Architecture violations fail the build instead of relying on review discipline.
