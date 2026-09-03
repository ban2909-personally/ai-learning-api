package com.ailearning.platform.learning.api.usecase;

import com.ailearning.platform.catalog.api.contract.LessonMediaStream;
import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;

import java.util.UUID;

public interface LessonMediaAccessUseCase {
    LessonMediaStream open(
            UUID userId,
            String courseSlug,
            UUID lessonId,
            MediaRangeRequest range
    );
}
