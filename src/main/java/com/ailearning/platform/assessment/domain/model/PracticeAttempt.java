package com.ailearning.platform.assessment.domain.model;

import java.util.Map;
import java.util.UUID;

public record PracticeAttempt(
        UUID id, UUID examId, UUID ownerId, String status, Map<UUID, String> answers) {
    public PracticeAttempt {
        answers = Map.copyOf(answers);
    }
}
