package com.ailearning.platform.catalog.adapter.out.cache.redis;

import com.ailearning.platform.catalog.application.port.out.PopularCatalogCache;
import com.ailearning.platform.catalog.config.PopularCatalogCacheProperties;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisPopularCatalogCache implements PopularCatalogCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPopularCatalogCache.class);
    private static final long WARNING_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PopularCatalogCacheProperties properties;
    private final Counter hits;
    private final Counter misses;
    private final Counter failures;
    private final AtomicLong nextWarningAt = new AtomicLong();

    public RedisPopularCatalogCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            PopularCatalogCacheProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.hits = counter(meterRegistry, "hit");
        this.misses = counter(meterRegistry, "miss");
        this.failures = counter(meterRegistry, "failure");
    }

    @Override
    public Optional<PageResult<Course>> find(int pageSize) {
        String key = key(pageSize);
        final String payload;
        try {
            payload = redis.opsForValue().get(key);
        } catch (DataAccessException exception) {
            reportFailure("read", exception);
            return Optional.empty();
        }

        if (payload == null) {
            misses.increment();
            return Optional.empty();
        }

        try {
            CachedPage cached = objectMapper.readValue(payload, CachedPage.class);
            PageResult<Course> page = cached.toPageResult();
            hits.increment();
            return Optional.of(page);
        } catch (JsonProcessingException | RuntimeException exception) {
            reportFailure("deserialize", exception);
            discardCorruptValue(key);
            return Optional.empty();
        }
    }

    @Override
    public void put(int pageSize, PageResult<Course> page) {
        try {
            String payload = objectMapper.writeValueAsString(CachedPage.from(page));
            redis.opsForValue().set(key(pageSize), payload, properties.ttl());
        } catch (JsonProcessingException | DataAccessException exception) {
            reportFailure("write", exception);
        }
    }

    private String key(int pageSize) {
        return properties.keyPrefix() + ":size:" + pageSize;
    }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("catalog.cache.access")
                .description("Popular catalog cache access outcomes")
                .tag("result", result)
                .register(registry);
    }

    private void discardCorruptValue(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            reportFailure("discard", exception);
        }
    }

    private void reportFailure(String operation, Exception exception) {
        failures.increment();
        long now = System.nanoTime();
        long next = nextWarningAt.get();
        if (now >= next && nextWarningAt.compareAndSet(next, now + WARNING_INTERVAL_NANOS)) {
            LOGGER.warn(
                    "Popular catalog cache {} failed; continuing with PostgreSQL: {}",
                    operation,
                    exception.getMessage()
            );
            LOGGER.debug("Popular catalog cache failure details", exception);
        }
    }

    private record CachedPage(
            List<Course> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        private static CachedPage from(PageResult<Course> source) {
            return new CachedPage(
                    source.content(),
                    source.page(),
                    source.size(),
                    source.totalElements(),
                    source.totalPages()
            );
        }

        private PageResult<Course> toPageResult() {
            return new PageResult<>(content, page, size, totalElements, totalPages);
        }
    }
}
