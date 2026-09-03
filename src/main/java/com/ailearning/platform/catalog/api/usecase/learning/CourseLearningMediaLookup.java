package com.ailearning.platform.catalog.api.usecase.learning;

import com.ailearning.platform.catalog.api.contract.LessonMediaStream;
import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;

import java.util.UUID;

public interface CourseLearningMediaLookup {
    LessonMediaStream openPublishedLessonMedia(
            String courseSlug,
            UUID lessonId,
            MediaRangeRequest range
    );
}
