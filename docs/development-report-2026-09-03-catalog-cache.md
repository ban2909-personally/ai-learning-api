# Development report: resilient popular catalog cache

Date: 2026-09-03

Branch: `feature/catalog-cache-resilience`

## Delivered outcome

The public first page of the unfiltered course catalog now uses a Redis cache-aside path. Repeated popular-page
reads avoid PostgreSQL while filtered searches and later pages preserve their existing JPA behavior. Redis is an
optional performance dependency: unavailable, timed-out, or corrupt cache state falls back to PostgreSQL without
changing the REST response.

## Architecture and maintainability

- Added the meaningful `PopularCatalogCache` output port in the catalog application boundary.
- Added the Redis implementation under `catalog/adapter/out/cache/redis`; controllers, domain models, and JPA
  adapters do not know about Redis.
- Kept eligibility policy in `CatalogService`: page zero, no search/category/level/price filters, validated size.
- Bounded key cardinality to the 50 valid page sizes and used a configurable versioned prefix.
- Did not add an unused eviction API. A future catalog write use case owns invalidation after its database
  transaction succeeds, as recorded in the architecture plan.
- Disabled Redis repository scanning because this application uses an explicit adapter, not Redis repositories.

## Performance and resilience

- Uses cache-aside hit -> return, miss -> PostgreSQL -> cache flow with a configurable five-minute default TTL.
- Stores JSON using `StringRedisTemplate`; Java native serialization and polymorphic typing are not used.
- Every value receives a TTL, bounding stale data and preventing persistent cache entries.
- Cache errors are contained both by the adapter and application fallback boundary.
- Warning logs are rate-limited to one per minute per adapter instance, with stack details at debug level.
- Micrometer exposes `catalog.cache.access` counters tagged `hit`, `miss`, and `failure`.
- Arbitrary search text is never included in Redis keys, limiting memory abuse and avoiding identity/token data in
  cache storage.

## Verification

- `mvn clean verify`: 63 tests, 0 failures, 0 errors, 0 skipped after the final metrics assertions.
- Redis 7.4 Testcontainers: JSON round-trip, deterministic key, TTL, corrupt-value removal, and metrics.
- PostgreSQL Testcontainers: an unfiltered REST catalog request succeeds with Redis forced unavailable.
- Existing MinIO, media, identity, enrollment, lesson-progress, Flyway V1-V6, and JPA tests remain green.
- Four ArchUnit rules, Spring Modulith verification/module test, and the JaCoCo threshold remain green.

## Files changed

- `docs/architecture/phase-2-popular-catalog-cache-plan.md`
- `src/main/java/com/ailearning/platform/catalog/application/port/out/PopularCatalogCache.java`
- `src/main/java/com/ailearning/platform/catalog/application/service/impl/CatalogService.java`
- `src/main/java/com/ailearning/platform/catalog/adapter/out/cache/redis/RedisPopularCatalogCache.java`
- `src/main/java/com/ailearning/platform/catalog/config/PopularCatalogCacheProperties.java`
- `src/main/java/com/ailearning/platform/catalog/config/CatalogModuleConfig.java`
- `src/main/resources/application.yml`
- `.env.example`
- `src/test/java/com/ailearning/platform/catalog/application/service/impl/CatalogServiceTest.java`
- `src/test/java/com/ailearning/platform/catalog/adapter/out/cache/redis/RedisPopularCatalogCacheIntegrationTest.java`
- `src/test/java/com/ailearning/platform/catalog/api/CatalogApiIntegrationTest.java`

## Known boundary

This slice deliberately does not claim distributed cache-stampede prevention. Add distributed single-flight or a
soft-expiry/stale-while-refresh policy only when load measurements justify the coordination complexity. Catalog
write/invalidation and deployment-level Redis memory/eviction policy also remain separate responsibilities.

## Branch verification

Feature HEAD `f553f9c` passed GitHub Actions CI run
[33734809703](https://github.com/ban2909-personally/ai-learning-api/actions/runs/33734809703). The documentation-only
completion commit must also pass the same workflow before merge.

## References

- [Redis cache-aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
- [Spring Data Redis template serialization](https://docs.spring.io/spring-data/redis/reference/redis/template.html)
