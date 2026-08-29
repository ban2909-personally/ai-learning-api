package com.ailearning.platform.catalog.adapter.out.persistence;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CourseJpaEntity;
import com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository.CategoryJpaRepository;
import com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository.CourseJpaRepository;
import com.ailearning.platform.catalog.adapter.out.persistence.mapper.CatalogPersistenceMapper;
import com.ailearning.platform.catalog.application.port.out.CatalogStore;
import com.ailearning.platform.catalog.application.query.CatalogQuery;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class CatalogPersistenceAdapter implements CatalogStore {
    private final CourseJpaRepository courses;
    private final CategoryJpaRepository categories;
    public CatalogPersistenceAdapter(CourseJpaRepository courses, CategoryJpaRepository categories) {
        this.courses = courses; this.categories = categories;
    }
    @Override
    public PageResult<Course> findPublished(CatalogQuery query) {
        var pageable = PageRequest.of(query.page(), query.size(), Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id")));
        var page = courses.findAll(specification(query), pageable).map(CatalogPersistenceMapper::toDomain);
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    @Override public Optional<Course> findPublishedBySlug(String slug) {
        return courses.findBySlugAndStatus(slug, CourseStatus.PUBLISHED).map(CatalogPersistenceMapper::toDomain);
    }
    @Override public Optional<Course> findPublishedById(UUID id) {
        return courses.findByIdAndStatus(id, CourseStatus.PUBLISHED).map(CatalogPersistenceMapper::toDomain);
    }
    @Override public List<Category> findCategories() {
        return categories.findAllByOrderByDisplayOrderAscNameAsc().stream().map(CatalogPersistenceMapper::toDomain).toList();
    }
    private Specification<CourseJpaEntity> specification(CatalogQuery query) {
        return (root, cq, builder) -> {
            if (!Long.class.equals(cq.getResultType()) && !long.class.equals(cq.getResultType())) {
                root.fetch("category", JoinType.INNER); root.fetch("instructor", JoinType.INNER);
            }
            cq.distinct(true);
            var predicate = builder.equal(root.get("status"), CourseStatus.PUBLISHED);
            if (hasText(query.search())) {
                String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("shortDescription")), pattern)));
            }
            if (hasText(query.category())) predicate = builder.and(predicate, builder.equal(root.get("category").get("slug"), query.category().trim()));
            if (query.level() != null) predicate = builder.and(predicate, builder.equal(root.get("level"), query.level()));
            if (query.minPrice() != null) predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("price"), query.minPrice()));
            if (query.maxPrice() != null) predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("price"), query.maxPrice()));
            return predicate;
        };
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
