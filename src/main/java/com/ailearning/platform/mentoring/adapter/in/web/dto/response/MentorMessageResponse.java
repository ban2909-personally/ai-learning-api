package com.ailearning.platform.mentoring.adapter.in.web.dto.response;

import com.ailearning.platform.mentoring.api.contract.MentorMessageView;
import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;

import java.time.Instant;
import java.util.UUID;

public record MentorMessageResponse(
        UUID id,
        MentorMessageRole role,
        String content,
        Instant createdAt
) {
    public static MentorMessageResponse from(MentorMessageView view) {
        return new MentorMessageResponse(view.id(), view.role(), view.content(), view.createdAt());
    }
}
