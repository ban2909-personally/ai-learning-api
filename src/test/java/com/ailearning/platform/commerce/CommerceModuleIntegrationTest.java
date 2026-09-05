package com.ailearning.platform.commerce;

import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.commerce.adapter.out.persistence.jpa.repository.CourseOrderJpaRepository;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.api.usecase.access.EnrollmentAccessLookup;
import com.ailearning.platform.platform.security.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@ApplicationModuleTest
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class CommerceModuleIntegrationTest {
    @MockitoBean PublishedCourseLookup courses;
    @MockitoBean UserLookup users;
    @MockitoBean EnrollmentAccessLookup enrollmentAccess;
    @MockitoBean CourseOrderJpaRepository orders;
    @MockitoBean Clock clock;
    @MockitoBean PlatformTransactionManager transactionManager;
    @MockitoBean CorsProperties corsProperties;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void moduleBootstrapsInIsolation() {
    }
}
