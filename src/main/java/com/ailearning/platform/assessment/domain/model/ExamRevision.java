package com.ailearning.platform.assessment.domain.model;

import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;

import java.util.UUID;

public record ExamRevision(
        UUID id,
        UUID seriesId,
        UUID authorId,
        int revision,
        ExamRevisionStatus status,
        long version,
        PracticeExam exam) {}
