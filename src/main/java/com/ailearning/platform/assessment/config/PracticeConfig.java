package com.ailearning.platform.assessment.config;

import com.ailearning.platform.assessment.api.usecase.PracticeUseCase;
import com.ailearning.platform.assessment.application.port.out.PracticeStore;
import com.ailearning.platform.assessment.application.service.impl.PracticeService;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PracticeConfig {
    @Bean
    PracticeUseCase practiceUseCase(PracticeStore store, AccountAccess access) {
        return new PracticeService(store, access);
    }
}
