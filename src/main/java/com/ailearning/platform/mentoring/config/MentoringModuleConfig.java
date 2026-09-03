package com.ailearning.platform.mentoring.config;

import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContextLookup;
import com.ailearning.platform.mentoring.adapter.out.ai.openai.OpenAiResponsesMentorClient;
import com.ailearning.platform.mentoring.api.usecase.MentorUseCase;
import com.ailearning.platform.mentoring.application.port.out.MentorAiClient;
import com.ailearning.platform.mentoring.application.port.out.MentorConversationStore;
import com.ailearning.platform.mentoring.application.port.out.MentorQuota;
import com.ailearning.platform.mentoring.application.port.out.MentorTurnMonitor;
import com.ailearning.platform.mentoring.application.service.impl.MentorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
public class MentoringModuleConfig {
    @Bean
    MentorUseCase mentorUseCase(
            MentoringLessonContextLookup lessonContexts,
            MentorConversationStore conversations,
            MentorQuota quota,
            MentorAiClient aiClient,
            MentorTurnMonitor monitor,
            Clock clock
    ) {
        return new MentorService(lessonContexts, conversations, quota, aiClient, monitor, clock);
    }

    @Bean
    MentorAiClient mentorAiClient(ObjectMapper objectMapper, OpenAiMentorProperties properties) {
        return new OpenAiResponsesMentorClient(objectMapper, properties);
    }

    @Bean("mentorTaskExecutor")
    TaskExecutor mentorTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mentor-stream-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}

