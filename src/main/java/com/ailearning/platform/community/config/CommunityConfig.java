package com.ailearning.platform.community.config;

import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.application.service.impl.CommunityService;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CommunityConfig {
    @Bean
    CommunityUseCase communityUseCase(CommunityStore store, AccountAccess accounts, Clock clock) {
        return new CommunityService(store, accounts, clock);
    }
}
