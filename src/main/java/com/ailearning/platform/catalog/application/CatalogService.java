package com.ailearning.platform.catalog.application;

import com.ailearning.platform.catalog.api.CategoryResponse;
import com.ailearning.platform.catalog.api.CourseDetailResponse;
import com.ailearning.platform.catalog.api.CourseSummaryResponse;
import com.ailearning.platform.catalog.domain.CourseEntity;
import com.ailearning.platform.catalog.domain.CourseStatus;
import com.ailearning.platform.catalog.infrastructure.CategoryRepository;
import com.ailearning.platform.catalog.infrastructure.CourseRepository;
import com.ailearning.platform.shared.api.PageResponse;
import com.ailearning.platform.shared.error.ApiException;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CatalogService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogQueryValidator queryValidator;

    public CatalogService(
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            CatalogQueryValidator queryValidator
    ) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.queryValidator = queryValidator;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryResponse> findPublishedCourses(CatalogQuery query) {
        queryValidator.validate(query);
        PageRequest pageable = PageRequest.of(
                query.page(), query.size(), Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id"))
        );
        Page<CourseEntity> courses = courseRepository.findAll(publicCatalogSpecification(query), pageable);
        return PageResponse.from(courses, CourseSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse findPublishedCourse(String slug) {
        return courseRepository.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
                .map(CourseDetailResponse::from)
                .orElseThrow(() -> new ApiException(
                        "course_not_found", HttpStatus.NOT_FOUND, "Không tìm thấy khóa học đã xuất bản."
                ));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc()
                .stream().map(CategoryResponse::from).toList();
    }

    private Specification<CourseEntity> publicCatalogSpecification(CatalogQuery query) {
        return (root, criteriaQuery, builder) -> {
            if (!Long.class.equals(criteriaQuery.getResultType()) && !long.class.equals(criteriaQuery.getResultType())) {
                root.fetch("category", JoinType.INNER);
                root.fetch("instructor", JoinType.INNER);
            }
            criteriaQuery.distinct(true);
            var predicate = builder.equal(root.get("status"), CourseStatus.PUBLISHED);
            if (hasText(query.search())) {
                String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("shortDescription")), pattern)
                ));
            }
            if (hasText(query.category())) {
                predicate = builder.and(predicate,
                        builder.equal(root.get("category").get("slug"), query.category().trim()));
            }
            if (query.level() != null) {
                predicate = builder.and(predicate, builder.equal(root.get("level"), query.level()));
            }
            if (query.minPrice() != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("price"), query.minPrice()));
            }
            if (query.maxPrice() != null) {
                predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("price"), query.maxPrice()));
            }
            return predicate;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
