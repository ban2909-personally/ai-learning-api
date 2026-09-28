package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.PostFeatures;

import java.util.UUID;

public record CreatePostCommand(
        String body, UUID spaceId, UUID sharedPostId, PostFeatures features) {}
