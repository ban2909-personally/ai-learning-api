package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.PollKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PollView(
        PollKind kind,
        String question,
        List<PollOptionView> options,
        long totalVotes,
        UUID myOptionId,
        Instant closesAt,
        boolean closed) {}
