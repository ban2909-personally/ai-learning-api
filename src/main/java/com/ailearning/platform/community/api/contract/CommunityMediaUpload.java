package com.ailearning.platform.community.api.contract;

import java.io.InputStream;

public record CommunityMediaUpload(String contentType, long sizeBytes, InputStream content) {}
