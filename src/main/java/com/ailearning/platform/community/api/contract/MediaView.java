package com.ailearning.platform.community.api.contract;

import java.util.UUID;

public record MediaView(UUID id, String contentType, long sizeBytes) {}
