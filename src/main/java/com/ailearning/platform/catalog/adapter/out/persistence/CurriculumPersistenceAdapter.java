package com.ailearning.platform.catalog.adapter.out.persistence;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository.CourseSectionJpaRepository;
import com.ailearning.platform.catalog.api.contract.CourseCurriculumView;
import com.ailearning.platform.catalog.application.port.out.CurriculumStore;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class CurriculumPersistenceAdapter implements CurriculumStore {
    private final CourseSectionJpaRepository sections;
    public CurriculumPersistenceAdapter(CourseSectionJpaRepository sections) { this.sections = sections; }
    @Override public Optional<CourseCurriculumView> findPublishedByCourseSlug(String courseSlug) {
        var rows = sections.findAllByCourseSlugAndCourseStatusOrderByDisplayOrderAsc(courseSlug, CourseStatus.PUBLISHED);
        if (rows.isEmpty()) return Optional.empty();
        var course = rows.getFirst().getCourse();
        var views = rows.stream().map(section -> new CourseCurriculumView.Section(section.getId(), section.getTitle(),
                section.getDisplayOrder(), section.getLessons().stream().map(lesson -> new CourseCurriculumView.Lesson(
                lesson.getId(), lesson.getTitle(), lesson.getDurationSeconds(), lesson.isPreview(),
                lesson.isPreview() ? lesson.getContentUrl() : null, lesson.getDisplayOrder())).toList())).toList();
        return Optional.of(new CourseCurriculumView(course.getId(), course.getSlug(), course.getTitle(), views));
    }
}
