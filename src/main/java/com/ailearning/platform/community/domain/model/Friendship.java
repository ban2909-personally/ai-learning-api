package com.ailearning.platform.community.domain.model;

import java.util.UUID;

public record Friendship(UUID initiatorId, String status) {}
