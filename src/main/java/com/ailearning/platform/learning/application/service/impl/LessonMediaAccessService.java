package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonMediaStream;
import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningMediaLookup;
import com.ailearning.platform.learning.api.usecase.LessonAccessUseCase;
import com.ailearning.platform.learning.api.usecase.LessonMediaAccessUseCase;

import java.util.UUID;

public class LessonMediaAccessService implements LessonMediaAccessUseCase {
    private final LessonAccessUseCase lessonAccess;
    private final CourseLearningMediaLookup media;

    public LessonMediaAccessService(LessonAccessUseCase lessonAccess, CourseLearningMediaLookup media) {
        this.lessonAccess = lessonAccess;
        this.media = media;
    }

    @Override
    public LessonMediaStream open(
            UUID userId,
            String courseSlug,
            UUID lessonId,
            MediaRangeRequest range
    ) {
        lessonAccess.openLesson(userId, courseSlug, lessonId);
        return media.openPublishedLessonMedia(courseSlug, lessonId, range);
    }
}
