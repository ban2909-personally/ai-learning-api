package com.ailearning.platform.catalog.api.contract;

import java.io.InputStream;
import java.io.Closeable;
import java.io.IOException;

public record LessonMediaStream(
        InputStream content,
        String contentType,
        long start,
        long length,
        long totalLength,
        String etag
) implements Closeable {
    @Override
    public void close() throws IOException {
        content.close();
    }
}
