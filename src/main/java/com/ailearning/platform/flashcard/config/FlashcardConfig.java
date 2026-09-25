package com.ailearning.platform.flashcard.config;

import com.ailearning.platform.flashcard.api.usecase.FlashcardUseCase;
import com.ailearning.platform.flashcard.application.port.out.FlashcardStore;
import com.ailearning.platform.flashcard.application.service.impl.FlashcardService;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlashcardConfig {
    @Bean
    FlashcardUseCase flashcards(FlashcardStore store, AccountAccess access) {
        return new FlashcardService(store, access);
    }
}
