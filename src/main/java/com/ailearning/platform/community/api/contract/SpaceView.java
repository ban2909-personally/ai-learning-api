package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.MemberStatus;
import com.ailearning.platform.community.domain.model.SpaceKind;
import com.ailearning.platform.community.domain.model.SpaceVisibility;

import java.time.Instant;
import java.util.UUID;

public record SpaceView(
        UUID id,
        String name,
        String description,
        SpaceKind kind,
        SpaceVisibility visibility,
        UUID ownerId,
        String ownerName,
        long memberCount,
        MemberRole myRole,
        MemberStatus myStatus,
        Instant createdAt) {}
