package com.ailearning.platform.mentoring.api.contract;

public record MentorTurnView(
        MentorMessageView userMessage,
        MentorMessageView assistantMessage,
        int remainingQuota
) {
}

