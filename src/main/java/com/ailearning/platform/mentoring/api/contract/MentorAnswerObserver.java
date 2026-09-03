package com.ailearning.platform.mentoring.api.contract;

public interface MentorAnswerObserver {
    void accepted(MentorMessageView userMessage, int remainingQuota);

    void delta(String text);

    void completed(MentorTurnView turn);
}

