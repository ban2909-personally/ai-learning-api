package com.ailearning.platform.commerce.config;

import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.commerce.adapter.in.transaction.TransactionalCourseOrderUseCase;
import com.ailearning.platform.commerce.application.port.out.CourseOrderStore;
import com.ailearning.platform.commerce.application.service.impl.CourseOrderService;
import com.ailearning.platform.commerce.domain.policy.PaidCourseOrderPolicy;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.api.usecase.access.EnrollmentAccessLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class CommerceModuleConfig {
    @Bean
    PaidCourseOrderPolicy paidCourseOrderPolicy() {
        return new PaidCourseOrderPolicy();
    }

    @Bean
    TransactionalCourseOrderUseCase courseOrderUseCase(
            PublishedCourseLookup courses,
            UserLookup users,
            EnrollmentAccessLookup enrollmentAccess,
            CourseOrderStore orders,
            PaidCourseOrderPolicy policy,
            Clock clock,
            CommerceOrderProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        var core = new CourseOrderService(
                courses,
                users,
                enrollmentAccess,
                orders,
                policy,
                clock,
                properties.pendingTtl()
        );
        TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        return new TransactionalCourseOrderUseCase(core, writeTransaction, readTransaction);
    }
}
