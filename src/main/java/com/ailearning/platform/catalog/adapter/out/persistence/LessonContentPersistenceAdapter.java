package com.ailearning.platform.catalog.adapter.out.persistence;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository.LessonJpaRepository;
import com.ailearning.platform.catalog.api.contract.LessonContentView;
import com.ailearning.platform.catalog.application.port.out.LessonContentStore;
import com.ailearning.platform.catalog.application.port.out.LessonMediaCatalog;
import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.catalog.domain.model.LessonMediaTarget;
import com.ailearning.platform.catalog.domain.model.PublishedLessonMedia;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Component
public class LessonContentPersistenceAdapter implements LessonContentStore, LessonMediaCatalog {
    private final LessonJpaRepository lessons;
    public LessonContentPersistenceAdapter(LessonJpaRepository lessons) { this.lessons = lessons; }
    @Override public Optional<LessonContentView> findPublishedLesson(String courseSlug, UUID lessonId) {
        return lessons.findByIdAndSectionCourseSlugAndSectionCourseStatus(lessonId, courseSlug, CourseStatus.PUBLISHED)
                .map(lesson -> new LessonContentView(lesson.getSection().getCourse().getId(), courseSlug,
                        lesson.getSection().getId(), lesson.getId(), lesson.getTitle(), lesson.getContentUrl(),
                        lesson.getDurationSeconds(), lesson.isPreview()));
    }

    @Override
    public Optional<LessonMediaTarget> findForManagement(String courseSlug, UUID lessonId) {
        return lessons.findByIdAndSectionCourseSlug(lessonId, courseSlug)
                .map(lesson -> new LessonMediaTarget(
                        lesson.getSection().getCourse().getId(),
                        courseSlug,
                        lesson.getId(),
                        lesson.getSection().getCourse().getInstructor().getId()
                ));
    }

    @Override
    public Optional<PublishedLessonMedia> findPublished(String courseSlug, UUID lessonId) {
        return lessons.findByIdAndSectionCourseSlugAndSectionCourseStatus(
                        lessonId,
                        courseSlug,
                        CourseStatus.PUBLISHED
                )
                .filter(lesson -> lesson.getMediaObjectKey() != null)
                .map(lesson -> new PublishedLessonMedia(
                        lesson.getSection().getCourse().getId(),
                        courseSlug,
                        lesson.getId(),
                        new LessonMediaAsset(
                                lesson.getMediaObjectKey(),
                                lesson.getMediaContentType(),
                                lesson.getMediaSizeBytes(),
                                lesson.getMediaEtag()
                        )
                ));
    }

    @Override
    @Transactional
    public void attach(UUID lessonId, LessonMediaAsset media, String contentUrl) {
        var lesson = lessons.findById(lessonId).orElseThrow(() ->
                new IllegalStateException("Lesson disappeared while attaching media"));
        lesson.attachMedia(
                media.objectKey(),
                media.contentType(),
                media.sizeBytes(),
                media.etag(),
                contentUrl
        );
    }
}
