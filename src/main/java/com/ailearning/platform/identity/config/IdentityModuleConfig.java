package com.ailearning.platform.identity.config;

import com.ailearning.platform.identity.adapter.in.transaction.TransactionalIdentityUseCase;
import com.ailearning.platform.identity.api.usecase.IdentityUseCase;
import com.ailearning.platform.identity.application.port.out.*;
import com.ailearning.platform.identity.application.service.impl.IdentityService;
import com.ailearning.platform.identity.domain.policy.PasswordPolicy;
import com.ailearning.platform.platform.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;

@Configuration
public class IdentityModuleConfig {
    @Bean
    TransactionalIdentityUseCase identityUseCase(UserStore users, RefreshSessionStore sessions,
            PasswordCodec passwords, AccessTokenIssuer tokens, TokenDigest digests, Clock clock,
            SecurityProperties properties, PlatformTransactionManager transactionManager) {
        IdentityService core = new IdentityService(users, sessions, passwords, new PasswordPolicy(), tokens, digests,
                clock, properties.accessTokenTtl(), properties.refreshTokenTtl());
        return new TransactionalIdentityUseCase(core, core, new TransactionTemplate(transactionManager));
    }
}
