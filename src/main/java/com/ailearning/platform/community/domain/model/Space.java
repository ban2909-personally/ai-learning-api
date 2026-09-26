package com.ailearning.platform.community.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Space(
        UUID id,
        UUID ownerId,
        SpaceKind kind,
        SpaceVisibility visibility,
        String name,
        String description,
        Instant createdAt) {}
