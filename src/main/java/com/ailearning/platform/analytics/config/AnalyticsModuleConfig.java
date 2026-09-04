package com.ailearning.platform.analytics.config;

import com.ailearning.platform.analytics.api.usecase.LearningAnalyticsUseCase;
import com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsStore;
import com.ailearning.platform.analytics.application.service.impl.LearningAnalyticsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AnalyticsModuleConfig {
    @Bean
    LearningAnalyticsUseCase learningAnalyticsUseCase(
            CompletionAnalyticsStore analytics,
            Clock clock
    ) {
        return new LearningAnalyticsService(analytics, clock);
    }
}
