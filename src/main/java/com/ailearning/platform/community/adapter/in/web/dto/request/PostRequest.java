package com.ailearning.platform.community.adapter.in.web.dto.request;

import com.ailearning.platform.community.domain.model.PostFeatures;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PostRequest(
        @NotNull @Size(max = 5000) String body,
        UUID spaceId,
        UUID sharedPostId,
        PostFeatures features) {}
