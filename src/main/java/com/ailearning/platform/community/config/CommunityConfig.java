package com.ailearning.platform.community.config;

import com.ailearning.platform.community.api.usecase.CommunityDiscoveryUseCase;
import com.ailearning.platform.community.api.usecase.CommunityMediaUseCase;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.api.usecase.DirectChatUseCase;
import com.ailearning.platform.community.api.usecase.MediaRetentionUseCase;
import com.ailearning.platform.community.api.usecase.PollUseCase;
import com.ailearning.platform.community.api.usecase.SpaceChatUseCase;
import com.ailearning.platform.community.application.port.out.CommunityMediaStorage;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.application.port.out.DirectChatStore;
import com.ailearning.platform.community.application.port.out.MediaRetentionStore;
import com.ailearning.platform.community.application.port.out.PostFeatureStore;
import com.ailearning.platform.community.application.port.out.SpaceChatStore;
import com.ailearning.platform.community.application.service.impl.CommunityDiscoveryService;
import com.ailearning.platform.community.application.service.impl.CommunityMediaService;
import com.ailearning.platform.community.application.service.impl.CommunityService;
import com.ailearning.platform.community.application.service.impl.DirectChatService;
import com.ailearning.platform.community.application.service.impl.MediaRetentionService;
import com.ailearning.platform.community.application.service.impl.PollService;
import com.ailearning.platform.community.application.service.impl.SpaceChatService;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.api.usecase.access.PublicProfileLookup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class CommunityConfig {
    @Bean
    CommunityDiscoveryUseCase communityDiscoveryUseCase(
            CommunityStore store, PublicProfileLookup profiles, AccountAccess accounts) {
        return new CommunityDiscoveryService(store, profiles, accounts);
    }

    @Bean
    DirectChatUseCase directChatUseCase(DirectChatStore store, AccountAccess accounts) {
        return new DirectChatService(store, accounts);
    }

    @Bean
    PollUseCase pollUseCase(
            PostFeatureStore features,
            CommunityStore store,
            CommunityUseCase community,
            AccountAccess accounts,
            Clock clock) {
        return new PollService(features, store, community, accounts, clock);
    }

    @Bean
    MediaRetentionUseCase mediaRetentionUseCase(
            MediaRetentionStore store, CommunityMediaStorage storage, Clock clock) {
        return new MediaRetentionService(store, storage, clock);
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
    CommunityUseCase communityUseCase(
            CommunityStore store,
            AccountAccess accounts,
            PublicProfileLookup profiles,
            Clock clock) {
        return new CommunityService(store, accounts, profiles, clock);
    }
}
