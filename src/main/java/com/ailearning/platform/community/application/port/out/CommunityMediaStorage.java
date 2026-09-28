package com.ailearning.platform.community.application.port.out;

import java.io.InputStream;

public interface CommunityMediaStorage {
    String store(String objectKey, String type, long size, InputStream content);

    InputStream open(String objectKey, long start, long length);

    void delete(String objectKey);
}
