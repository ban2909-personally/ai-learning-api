package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberView(
        UUID userId,
        String displayName,
        String email,
        MemberRole role,
        MemberStatus status,
        Instant createdAt) {}
