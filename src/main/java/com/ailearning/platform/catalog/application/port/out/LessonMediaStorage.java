package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;

import java.io.InputStream;

public interface LessonMediaStorage {
    LessonMediaAsset store(String objectKey, String contentType, long sizeBytes, InputStream content);

    InputStream open(String objectKey, long offset, long length);

    void delete(String objectKey);
}
