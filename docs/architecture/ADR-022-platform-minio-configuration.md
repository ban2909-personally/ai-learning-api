# ADR-022: Neutral MinIO configuration in platform

Status: Accepted for implementation (2026-09-28).

## Context

Catalog currently owns the application's MinIO connection settings and singleton client, although these are technical infrastructure rather than course business rules. Assessment needs managed Listening audio next. It must not import catalog configuration or catalog storage adapters to obtain a client.

This is a small prerequisite increment, not a claim that exam audio upload already exists. Speaking stays out of scope. Do not modify existing database tables, object keys, buckets, stored files or upload limits.

## Decisions

- Move only `MinioStorageProperties` and creation of `MinioClient` to `platform/configuration/storage`. Keep property prefix `app.storage.minio`, environment variables, defaults and bean behavior unchanged.
- Expose this neutral configuration via a Spring Modulith named interface `platform::storage`. Catalog declares that dependency explicitly; only its configuration/outbound adapter can use it. Domain and application must not depend on platform.
- Keep `LessonMediaStorage`, media authorization, ownership, object keys, content types and upload/delivery use cases in catalog. Assessment will own its own meaningful audio port and adapter when audio is implemented.
- Do not add a generic storage interface, shared business entity, utility, wrapper client or premature starter artifact. The singleton SDK client is the shared technical capability; independent module adapters map their own business contracts.
- Keep credentials out of log-friendly representations. The properties record's `toString` must not expose the access key or secret key.
- Verify that configuration binds without contacting a server, rejects blank credentials, creates one client, and retains existing real MinIO store/range/delete behavior. Keep Modulith and ArchUnit checks mandatory.

## Migration and verification checklist

- [x] Move neutral properties/client configuration and declare `platform::storage`.
- [x] Remove duplicate catalog client configuration; update only adapter/configuration imports.
- [x] Configuration binding, credential redaction and invalid-settings tests.
- [x] Existing Testcontainers MinIO integration, module boundaries and full backend verify: 266 tests, 0 failures/errors/skips.
- [x] Preserve user changes, unchanged pom.xml, database behavior and upload limits.
- [ ] Feature commit/push → exact CI green → merge/push main → main CI evidence.

## Next product increment

Managed assessment audio will use immutable opaque asset IDs, strict upload size below 10,000,000 bytes, validated media content, author ownership and server-side read authorization. It must preserve historical revision audio and keep transcripts private before submission. The player/editor require responsive real-audio controls and range delivery tests. Those requirements remain unfinished by this configuration-only increment.
