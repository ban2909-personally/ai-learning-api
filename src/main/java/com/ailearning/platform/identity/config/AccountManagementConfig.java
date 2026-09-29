package com.ailearning.platform.identity.config;

import com.ailearning.platform.identity.adapter.in.transaction.TransactionalAccountManagement;
import com.ailearning.platform.identity.api.usecase.AccountManagementUseCase;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.api.usecase.access.PublicProfileLookup;
import com.ailearning.platform.identity.api.usecase.access.SocialProfileAccess;
import com.ailearning.platform.identity.application.port.out.*;
import com.ailearning.platform.identity.application.service.impl.AccountAccessService;
import com.ailearning.platform.identity.application.service.impl.AccountManagementService;
import com.ailearning.platform.identity.application.service.impl.PublicProfileService;
import com.ailearning.platform.identity.application.service.impl.SocialProfileService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AccountManagementConfig {
    @Bean
    SocialProfileAccess socialProfileAccess(SocialProfileStore store, AccountAccess accounts) {
        return new SocialProfileService(store, accounts);
    }

    @Bean
    PublicProfileLookup publicProfileLookup(PublicProfileStore store) {
        return new PublicProfileService(store);
    }

    @Bean
    AccountAccess accountAccess(UserStore users) {
        return new AccountAccessService(users);
    }

    @Bean
    AccountManagementUseCase accountManagement(
            AccountAdministrationStore store,
            AccountAccess access,
            PasswordCodec passwords,
            PlatformTransactionManager manager) {
        return new TransactionalAccountManagement(
                new AccountManagementService(store, access, passwords),
                new TransactionTemplate(manager));
    }
}
