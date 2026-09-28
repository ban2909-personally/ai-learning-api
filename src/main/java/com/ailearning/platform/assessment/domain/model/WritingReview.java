package com.ailearning.platform.assessment.domain.model;

import java.util.UUID;

public record WritingReview(
        UUID attemptId,
        UUID questionId,
        UUID reviewerId,
        int taskScore,
        int coherenceScore,
        int vocabularyScore,
        int grammarScore,
        String feedback) {
    public int totalScore() {
        return taskScore + coherenceScore + vocabularyScore + grammarScore;
    }
}
