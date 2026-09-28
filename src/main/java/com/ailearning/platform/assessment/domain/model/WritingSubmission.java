package com.ailearning.platform.assessment.domain.model;

import java.time.Instant;
import java.util.UUID;

public record WritingSubmission(
        UUID attemptId,
        UUID questionId,
        String examTitle,
        String prompt,
        String answer,
        Instant submittedAt) {}
