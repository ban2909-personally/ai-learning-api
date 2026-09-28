package com.ailearning.platform.community.domain.model;

import java.util.UUID;

public record MediaAsset(
        UUID id, UUID ownerId, String objectKey, String contentType, long sizeBytes, String etag) {}
