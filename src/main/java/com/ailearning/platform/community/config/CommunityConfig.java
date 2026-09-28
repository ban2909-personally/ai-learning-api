package com.ailearning.platform.community.config;

import com.ailearning.platform.community.api.usecase.CommunityMediaUseCase;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.api.usecase.SpaceChatUseCase;
import com.ailearning.platform.community.application.port.out.CommunityMediaStorage;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.application.port.out.SpaceChatStore;
import com.ailearning.platform.community.application.service.impl.CommunityMediaService;
import com.ailearning.platform.community.application.service.impl.CommunityService;
import com.ailearning.platform.community.application.service.impl.SpaceChatService;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class CommunityConfig {
    @Bean
    com.ailearning.platform.community.api.usecase.MediaRetentionUseCase mediaRetentionUseCase(
            com.ailearning.platform.community.application.port.out.MediaRetentionStore store,
            CommunityMediaStorage storage,
            Clock clock) {
        return new com.ailearning.platform.community.application.service.impl.MediaRetentionService(
                store, storage, clock);
    }

    @Bean
    SpaceChatUseCase spaceChatUseCase(
            CommunityStore store, SpaceChatStore chat, AccountAccess accounts) {
        return new SpaceChatService(store, chat, accounts);
    }

    @Bean
    CommunityMediaUseCase communityMediaUseCase(
            CommunityStore store,
            CommunityMediaStorage storage,
            CommunityUseCase community,
            AccountAccess accounts,
            Clock clock) {
        return new CommunityMediaService(store, storage, community, accounts, clock);
    }

    @Bean
    CommunityUseCase communityUseCase(CommunityStore store, AccountAccess accounts, Clock clock) {
        return new CommunityService(store, accounts, clock);
    }
}
