package com.ailearning.platform.catalog.adapter.in.web.dto.response;

import com.ailearning.platform.catalog.api.contract.LessonMediaView;

import java.util.UUID;

public record LessonMediaResponse(
        UUID courseId,
        String courseSlug,
        UUID lessonId,
        String contentType,
        long sizeBytes,
        String etag,
        String contentUrl
) {
    public static LessonMediaResponse from(LessonMediaView media) {
        return new LessonMediaResponse(
                media.courseId(),
                media.courseSlug(),
                media.lessonId(),
                media.contentType(),
                media.sizeBytes(),
                media.etag(),
                media.contentUrl()
        );
    }
}
