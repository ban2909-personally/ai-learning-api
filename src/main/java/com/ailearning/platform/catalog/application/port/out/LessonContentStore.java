package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.api.contract.LessonContentView;
import java.util.Optional;
import java.util.UUID;

public interface LessonContentStore {
    Optional<LessonContentView> findPublishedLesson(String courseSlug, UUID lessonId);
}
