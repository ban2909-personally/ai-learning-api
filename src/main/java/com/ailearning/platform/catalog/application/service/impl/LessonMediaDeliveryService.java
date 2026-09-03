package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonMediaStream;
import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningMediaLookup;
import com.ailearning.platform.catalog.application.port.out.LessonMediaCatalog;
import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.UUID;

public class LessonMediaDeliveryService implements CourseLearningMediaLookup {
    private final LessonMediaCatalog catalog;
    private final LessonMediaStorage storage;

    public LessonMediaDeliveryService(LessonMediaCatalog catalog, LessonMediaStorage storage) {
        this.catalog = catalog;
        this.storage = storage;
    }

    @Override
    public LessonMediaStream openPublishedLessonMedia(
            String courseSlug,
            UUID lessonId,
            MediaRangeRequest requestedRange
    ) {
        var lesson = catalog.findPublished(courseSlug, lessonId).orElseThrow(() ->
                new BusinessException(
                        "lesson_media_not_found",
                        ErrorType.NOT_FOUND,
                        "Bài học chưa có nội dung media."
                ));
        var asset = lesson.asset();
        var range = requestedRange.resolve(asset.sizeBytes());
        return new LessonMediaStream(
                storage.open(asset.objectKey(), range.start(), range.length()),
                asset.contentType(),
                range.start(),
                range.length(),
                asset.sizeBytes(),
                asset.etag()
        );
    }
}
