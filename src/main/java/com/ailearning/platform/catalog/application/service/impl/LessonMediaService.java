package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonMediaUpload;
import com.ailearning.platform.catalog.api.contract.LessonMediaView;
import com.ailearning.platform.catalog.api.usecase.LessonMediaManagementUseCase;
import com.ailearning.platform.catalog.application.port.out.LessonMediaCatalog;
import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.catalog.domain.policy.LessonMediaPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.UUID;

public class LessonMediaService implements LessonMediaManagementUseCase {
    private final LessonMediaCatalog catalog;
    private final LessonMediaStorage storage;
    private final LessonMediaPolicy policy;

    public LessonMediaService(
            LessonMediaCatalog catalog,
            LessonMediaStorage storage,
            LessonMediaPolicy policy
    ) {
        this.catalog = catalog;
        this.storage = storage;
        this.policy = policy;
    }

    @Override
    public LessonMediaView upload(
            UUID actorId,
            boolean administrator,
            String courseSlug,
            UUID lessonId,
            LessonMediaUpload upload
    ) {
        var target = catalog.findForManagement(courseSlug, lessonId).orElseThrow(() ->
                new BusinessException("lesson_not_found", ErrorType.NOT_FOUND, "Không tìm thấy bài học."));
        policy.ensureCanManage(actorId, administrator, target.instructorId());
        String contentType = policy.validateUpload(upload.contentType(), upload.sizeBytes());
        String objectKey = "courses/%s/lessons/%s/%s".formatted(
                target.courseId(),
                lessonId,
                UUID.randomUUID()
        );

        LessonMediaAsset media = storage.store(objectKey, contentType, upload.sizeBytes(), upload.content());
        String contentUrl = "/api/v1/media/courses/%s/lessons/%s".formatted(courseSlug, lessonId);
        try {
            catalog.attach(lessonId, media, contentUrl);
        } catch (RuntimeException persistenceFailure) {
            try {
                storage.delete(media.objectKey());
            } catch (RuntimeException cleanupFailure) {
                persistenceFailure.addSuppressed(cleanupFailure);
            }
            throw persistenceFailure;
        }

        return new LessonMediaView(
                target.courseId(),
                target.courseSlug(),
                target.lessonId(),
                media.contentType(),
                media.sizeBytes(),
                media.etag(),
                contentUrl
        );
    }
}
