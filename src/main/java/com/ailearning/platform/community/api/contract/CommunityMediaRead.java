package com.ailearning.platform.community.api.contract;

import java.io.IOException;
import java.io.InputStream;

public record CommunityMediaRead(
        String contentType,
        String etag,
        long totalSize,
        long start,
        long length,
        InputStream content)
        implements AutoCloseable {
    @Override
    public void close() throws IOException {
        if (content != null) content.close();
    }
}
