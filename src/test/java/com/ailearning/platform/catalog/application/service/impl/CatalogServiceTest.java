package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.application.port.out.CatalogStore;
import com.ailearning.platform.catalog.application.port.out.CurriculumStore;
import com.ailearning.platform.catalog.application.port.out.LessonContentStore;
import com.ailearning.platform.catalog.application.port.out.PopularCatalogCache;
import com.ailearning.platform.catalog.application.query.CatalogQuery;
import com.ailearning.platform.catalog.domain.enums.CourseLevel;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogServiceTest {
    private final CatalogStore store = mock(CatalogStore.class);
    private final CurriculumStore curricula = mock(CurriculumStore.class);
    private final LessonContentStore lessons = mock(LessonContentStore.class);
    private final PopularCatalogCache cache = mock(PopularCatalogCache.class);
    private final CatalogService service = new CatalogService(store, curricula, lessons, cache);
    private final CatalogQuery popularQuery = new CatalogQuery(
            null, null, null, null, null, 0, 12
    );

    @Test
    void returnsPopularPageFromCacheWithoutQueryingPostgres() {
        PageResult<Course> cached = page();
        when(cache.find(12)).thenReturn(Optional.of(cached));

        PageResult<Course> result = service.findPublishedCourses(popularQuery);

        assertThat(result).isSameAs(cached);
        verify(store, never()).findPublished(popularQuery);
    }

    @Test
    void loadsAndCachesPopularPageOnMiss() {
        PageResult<Course> loaded = page();
        when(cache.find(12)).thenReturn(Optional.empty());
        when(store.findPublished(popularQuery)).thenReturn(loaded);

        PageResult<Course> result = service.findPublishedCourses(popularQuery);

        assertThat(result).isSameAs(loaded);
        verify(cache).put(12, loaded);
    }

    @Test
    void bypassesCacheForUserControlledFilters() {
        CatalogQuery filtered = new CatalogQuery(
                "spring", null, null, null, null, 0, 12
        );
        PageResult<Course> loaded = page();
        when(store.findPublished(filtered)).thenReturn(loaded);

        assertThat(service.findPublishedCourses(filtered)).isSameAs(loaded);
        verifyNoInteractions(cache);
    }

    @Test
    void fallsBackToPostgresWhenCacheReadFails() {
        PageResult<Course> loaded = page();
        when(cache.find(12)).thenThrow(new IllegalStateException("Redis unavailable"));
        when(store.findPublished(popularQuery)).thenReturn(loaded);

        assertThat(service.findPublishedCourses(popularQuery)).isSameAs(loaded);
        verify(cache).put(12, loaded);
    }

    @Test
    void returnsPostgresResultWhenCacheWriteFails() {
        PageResult<Course> loaded = page();
        when(cache.find(12)).thenReturn(Optional.empty());
        when(store.findPublished(popularQuery)).thenReturn(loaded);
        doThrow(new IllegalStateException("Redis unavailable")).when(cache).put(12, loaded);

        assertThatCode(() -> assertThat(service.findPublishedCourses(popularQuery)).isSameAs(loaded))
                .doesNotThrowAnyException();
    }

    private PageResult<Course> page() {
        Category category = new Category(UUID.randomUUID(), "backend", "Backend", "Backend courses");
        Course course = new Course(
                UUID.randomUUID(),
                "spring-clean",
                "Spring Clean Architecture",
                "Build maintainable APIs",
                "Course description",
                CourseLevel.INTERMEDIATE,
                "vi",
                new BigDecimal("499000"),
                "VND",
                null,
                600,
                category,
                UUID.randomUUID(),
                "Instructor",
                Instant.parse("2026-09-03T00:00:00Z")
        );
        return new PageResult<>(List.of(course), 0, 12, 1, 1);
    }
}
