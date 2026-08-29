package com.ailearning.platform.learning.config;

import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.adapter.in.transaction.TransactionalEnrollmentUseCase;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.application.service.impl.EnrollmentService;
import com.ailearning.platform.learning.application.service.impl.LessonAccessService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;

@Configuration
public class LearningModuleConfig {
    @Bean
    LessonAccessService lessonAccessUseCase(CourseLearningContentLookup content, EnrollmentStore enrollments) {
        return new LessonAccessService(content, enrollments);
    }
    @Bean
    TransactionalEnrollmentUseCase enrollmentUseCase(PublishedCourseLookup courses, UserLookup users,
            EnrollmentStore enrollments, Clock clock, PlatformTransactionManager transactionManager) {
        EnrollmentService core = new EnrollmentService(courses, users, enrollments, clock);
        return new TransactionalEnrollmentUseCase(core, new TransactionTemplate(transactionManager));
    }
}
