package com.ailearning.platform.community.domain.model;

import java.time.Instant;
import java.util.List;

public record PollDefinition(
        PollKind kind, String question, List<String> options, Instant closesAt) {}
