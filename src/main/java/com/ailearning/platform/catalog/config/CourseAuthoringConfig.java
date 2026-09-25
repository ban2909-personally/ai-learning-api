package com.ailearning.platform.catalog.config;

import com.ailearning.platform.catalog.adapter.in.transaction.TransactionalCourseAuthoring;
import com.ailearning.platform.catalog.api.usecase.CourseAuthoringUseCase;
import com.ailearning.platform.catalog.application.port.out.CourseAuthoringStore;
import com.ailearning.platform.catalog.application.port.out.PopularCatalogCache;
import com.ailearning.platform.catalog.application.service.impl.CourseAuthoringService;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class CourseAuthoringConfig {
    @Bean
    CourseAuthoringUseCase courseAuthoring(
            CourseAuthoringStore store,
            AccountAccess accounts,
            PopularCatalogCache cache,
            PlatformTransactionManager manager) {
        return new TransactionalCourseAuthoring(
                new CourseAuthoringService(store, accounts),
                new TransactionTemplate(manager),
                cache);
    }
}
