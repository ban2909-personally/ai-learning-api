package com.ailearning.platform.catalog.api.usecase;

import com.ailearning.platform.catalog.api.contract.LessonMediaUpload;
import com.ailearning.platform.catalog.api.contract.LessonMediaView;

import java.util.UUID;

public interface LessonMediaManagementUseCase {
    LessonMediaView upload(
            UUID actorId,
            boolean administrator,
            String courseSlug,
            UUID lessonId,
            LessonMediaUpload upload
    );
}
