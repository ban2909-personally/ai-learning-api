package com.ailearning.platform.analytics.api.usecase;

import com.ailearning.platform.analytics.api.contract.LearningAnalyticsView;
import com.ailearning.platform.analytics.api.contract.ProjectLessonCompletionCommand;

import java.util.UUID;

public interface LearningAnalyticsUseCase {
    boolean projectLessonCompleted(ProjectLessonCompletionCommand command);

    LearningAnalyticsView findMine(UUID userId, int courseLimit);
}
