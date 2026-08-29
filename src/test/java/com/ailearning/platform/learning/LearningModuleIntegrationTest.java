package com.ailearning.platform.learning;

import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.adapter.out.persistence.jpa.repository.EnrollmentJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@ApplicationModuleTest
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class LearningModuleIntegrationTest {
    @MockitoBean PublishedCourseLookup courses;
    @MockitoBean UserLookup users;
    @MockitoBean Clock clock;
    @MockitoBean EnrollmentJpaRepository enrollments;
    @MockitoBean PlatformTransactionManager transactionManager;

    @Test
    void moduleBootstrapsInIsolation() {
    }
}
