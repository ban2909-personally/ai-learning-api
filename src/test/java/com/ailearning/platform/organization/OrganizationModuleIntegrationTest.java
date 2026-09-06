package com.ailearning.platform.organization;

import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.organization.adapter.out.persistence.jpa.repository.OrganizationJpaRepository;
import com.ailearning.platform.organization.adapter.out.persistence.jpa.repository.OrganizationMembershipJpaRepository;
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
class OrganizationModuleIntegrationTest {
    @MockitoBean UserLookup users;
    @MockitoBean OrganizationJpaRepository organizations;
    @MockitoBean OrganizationMembershipJpaRepository memberships;
    @MockitoBean Clock clock;
    @MockitoBean PlatformTransactionManager transactionManager;
    @MockitoBean CorsProperties corsProperties;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void moduleBootstrapsInIsolation() {
    }
}
