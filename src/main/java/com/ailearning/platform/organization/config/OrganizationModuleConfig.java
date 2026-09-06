package com.ailearning.platform.organization.config;

import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.organization.adapter.in.transaction.TransactionalOrganizationUseCase;
import com.ailearning.platform.organization.application.port.out.OrganizationStore;
import com.ailearning.platform.organization.application.service.impl.OrganizationService;
import com.ailearning.platform.organization.domain.policy.OrganizationMembershipPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class OrganizationModuleConfig {
    @Bean
    OrganizationMembershipPolicy organizationMembershipPolicy() {
        return new OrganizationMembershipPolicy();
    }

    @Bean
    TransactionalOrganizationUseCase organizationUseCase(
            UserLookup users,
            OrganizationStore organizations,
            OrganizationMembershipPolicy membershipPolicy,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        var core = new OrganizationService(users, organizations, membershipPolicy, clock);
        TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        return new TransactionalOrganizationUseCase(core, writeTransaction, readTransaction);
    }
}
