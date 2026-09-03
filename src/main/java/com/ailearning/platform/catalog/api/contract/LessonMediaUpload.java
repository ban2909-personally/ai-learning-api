package com.ailearning.platform.catalog.api.contract;

import java.io.InputStream;

public record LessonMediaUpload(
        String contentType,
        long sizeBytes,
        InputStream content
) {
}
