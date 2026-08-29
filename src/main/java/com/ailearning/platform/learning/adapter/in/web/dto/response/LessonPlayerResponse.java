package com.ailearning.platform.learning.adapter.in.web.dto.response;

import com.ailearning.platform.learning.api.contract.LessonPlayerView;
import java.util.UUID;

public record LessonPlayerResponse(UUID courseId, String courseSlug, UUID sectionId, UUID lessonId,
        String title, String contentUrl, int durationSeconds, boolean preview) {
    public static LessonPlayerResponse from(LessonPlayerView value) {
        return new LessonPlayerResponse(value.courseId(), value.courseSlug(), value.sectionId(), value.lessonId(),
                value.title(), value.contentUrl(), value.durationSeconds(), value.preview());
    }
}
