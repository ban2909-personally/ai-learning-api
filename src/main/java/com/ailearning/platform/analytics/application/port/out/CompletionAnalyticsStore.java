package com.ailearning.platform.analytics.application.port.out;

import com.ailearning.platform.analytics.domain.model.CompletionFact;

import java.util.UUID;

public interface CompletionAnalyticsStore {
    boolean append(CompletionFact fact);

    CompletionAnalyticsSnapshot summarize(UUID userId, int courseLimit);
}
