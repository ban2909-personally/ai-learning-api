package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.catalog.domain.model.LessonMediaTarget;
import com.ailearning.platform.catalog.domain.model.PublishedLessonMedia;

import java.util.Optional;
import java.util.UUID;

public interface LessonMediaCatalog {
    Optional<LessonMediaTarget> findForManagement(String courseSlug, UUID lessonId);

    Optional<PublishedLessonMedia> findPublished(String courseSlug, UUID lessonId);

    void attach(UUID lessonId, LessonMediaAsset media, String contentUrl);
}
