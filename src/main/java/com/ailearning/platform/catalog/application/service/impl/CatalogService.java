package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.api.usecase.CatalogUseCase;
import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.catalog.api.usecase.published.PublishedCurriculumLookup;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.catalog.api.contract.CourseCurriculumView;
import com.ailearning.platform.catalog.api.contract.LessonContentView;
import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import com.ailearning.platform.catalog.application.port.out.CatalogStore;
import com.ailearning.platform.catalog.application.port.out.CurriculumStore;
import com.ailearning.platform.catalog.application.port.out.LessonContentStore;
import com.ailearning.platform.catalog.application.query.CatalogQuery;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.catalog.application.query.CatalogQueryValidator;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CatalogService implements CatalogUseCase, PublishedCourseLookup, PublishedCurriculumLookup, CourseLearningContentLookup {
    private final CatalogStore store;
    private final CurriculumStore curricula;
    private final LessonContentStore lessons;
    private final CatalogQueryValidator validator = new CatalogQueryValidator();
    public CatalogService(CatalogStore store, CurriculumStore curricula, LessonContentStore lessons) {
        this.store = store; this.curricula = curricula; this.lessons = lessons;
    }
    @Override public PageResult<Course> findPublishedCourses(CatalogQuery query) {
        validator.validate(query); return store.findPublished(query);
    }
    @Override public Course findPublishedCourse(String slug) {
        return store.findPublishedBySlug(slug).orElseThrow(() -> new BusinessException("course_not_found", ErrorType.NOT_FOUND, "Không tìm thấy khóa học đã xuất bản."));
    }
    @Override public List<Category> findCategories() { return store.findCategories(); }
    @Override public Optional<PublishedCourseView> findPublishedBySlug(String slug) { return store.findPublishedBySlug(slug).map(this::toView); }
    @Override public Optional<PublishedCourseView> findPublishedById(UUID id) { return store.findPublishedById(id).map(this::toView); }
    @Override public CourseCurriculumView findPublishedCurriculum(String courseSlug) {
        return curricula.findPublishedByCourseSlug(courseSlug).orElseThrow(() -> new BusinessException(
                "course_curriculum_not_found", ErrorType.NOT_FOUND, "Không tìm thấy nội dung khóa học."));
    }
    @Override public Optional<LessonContentView> findPublishedLesson(String courseSlug, UUID lessonId) {
        return lessons.findPublishedLesson(courseSlug, lessonId);
    }
    private PublishedCourseView toView(Course course) {
        return new PublishedCourseView(course.id(), course.slug(), course.title(), course.shortDescription(),
                course.level().name(), course.price(), course.currency(), course.thumbnailUrl(),
                course.estimatedDurationMinutes(), new PublishedCourseView.CategoryView(course.category().id(),
                course.category().slug(), course.category().name(), course.category().description()), course.instructorName());
    }
}
