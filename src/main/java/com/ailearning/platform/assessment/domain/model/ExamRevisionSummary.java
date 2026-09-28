package com.ailearning.platform.assessment.domain.model;

import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;

import java.util.UUID;

public record ExamRevisionSummary(
        UUID id,
        String slug,
        String title,
        int durationMinutes,
        int revision,
        ExamRevisionStatus status,
        long version) {}
