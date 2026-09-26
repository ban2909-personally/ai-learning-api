package com.ailearning.platform.community.api.contract;

import java.util.UUID;

public record CreatePostCommand(String body, UUID spaceId, UUID sharedPostId) {}
