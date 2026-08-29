package com.ailearning.platform.catalog.adapter.out.persistence;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository.LessonJpaRepository;
import com.ailearning.platform.catalog.api.contract.LessonContentView;
import com.ailearning.platform.catalog.application.port.out.LessonContentStore;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class LessonContentPersistenceAdapter implements LessonContentStore {
    private final LessonJpaRepository lessons;
    public LessonContentPersistenceAdapter(LessonJpaRepository lessons) { this.lessons = lessons; }
    @Override public Optional<LessonContentView> findPublishedLesson(String courseSlug, UUID lessonId) {
        return lessons.findByIdAndSectionCourseSlugAndSectionCourseStatus(lessonId, courseSlug, CourseStatus.PUBLISHED)
                .map(lesson -> new LessonContentView(lesson.getSection().getCourse().getId(), courseSlug,
                        lesson.getSection().getId(), lesson.getId(), lesson.getTitle(), lesson.getContentUrl(),
                        lesson.getDurationSeconds(), lesson.isPreview()));
    }
}
