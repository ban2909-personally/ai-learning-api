# Phase 2 completion: resilient popular catalog cache

## Objective

Reduce repeated PostgreSQL work for the most frequently requested catalog page without changing its REST or
database contract. The first unfiltered published-course page uses Redis cache-aside; filtered searches and later
pages continue to read PostgreSQL directly so user-controlled search values cannot create an unbounded cache key
space.

## Architectural slice

```text
CatalogController
    -> CatalogUseCase
    -> CatalogService
        -> PopularCatalogCache output port -> Redis string/JSON adapter
        -> CatalogStore output port        -> JPA adapter
```

The application layer decides whether a query represents the popular page. The Redis adapter owns keys, JSON,
TTL, cache metrics, and Redis failure handling. The domain and REST adapters do not depend on Redis or Spring
Cache annotations.

## Cache policy

- Cache only page `0` when search, category, level, minimum price, and maximum price are absent.
- Include the validated page size in a versioned deterministic key; at most 50 page-size variants can exist.
- Store explicit JSON through `StringRedisTemplate`; do not use Java native serialization.
- Apply a configurable positive TTL to every value. Do not create persistent cache keys.
- Do not cache exceptions or missing/invalid query results.
- Treat Redis as optional: read, write, corrupt-payload, and timeout failures fall back to PostgreSQL and do not
  change the public response.
- Record hit, miss, and failure counters for operational visibility; rate-limit warning logs during outages.
- Disable Spring Data Redis repository scanning because this project uses Redis only through an adapter.

## Consistency and invalidation ownership

There is currently no course publication/update use case, so no unused eviction interface is introduced. TTL
bounds staleness in this slice. When catalog writes are added, the application service that commits the catalog
change must call a meaningful cache invalidation port after the database transaction succeeds. Cache update logic
must not be placed in controllers, JPA entities, or unrelated modules.

## Security and scale notes

- Cache keys never contain raw search input, identity, token, or personal data.
- JSON is deserialized into a fixed adapter document type; polymorphic/default typing is not enabled.
- A Redis outage increases database load but does not make the catalog unavailable.
- This slice does not claim cross-instance stampede protection. A distributed single-flight/soft-expiry strategy
  should be added before traffic requires it, based on measured miss concurrency and database headroom.

## Verification checklist

- [x] Application tests cover hit, miss, ineligible query, Redis read failure, and Redis write failure.
- [x] Redis Testcontainers verifies JSON round-trip, deterministic key, and positive bounded TTL.
- [x] A corrupt cached payload is discarded and treated as a miss.
- [x] Catalog REST responses and PostgreSQL behavior remain unchanged when Redis is unavailable.
- [x] Spring Modulith, ArchUnit, Flyway/JPA, JaCoCo, and all existing tests remain green.
- [x] The feature branch passes GitHub Actions before merge.

## References

- [Redis cache-aside guidance](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
- [Spring Data Redis template and serializer guidance](https://docs.spring.io/spring-data/redis/reference/redis/template.html)
