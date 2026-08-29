package com.ailearning.platform.catalog.api.usecase.learning;

import com.ailearning.platform.catalog.api.contract.LessonContentView;
import java.util.Optional;
import java.util.UUID;

public interface CourseLearningContentLookup {
    Optional<LessonContentView> findPublishedLesson(String courseSlug, UUID lessonId);
}
