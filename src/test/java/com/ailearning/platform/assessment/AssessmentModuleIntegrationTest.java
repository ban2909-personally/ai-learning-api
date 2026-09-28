package com.ailearning.platform.assessment;

import com.ailearning.platform.identity.api.usecase.access.AccountAccess;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@ApplicationModuleTest
@TestPropertySource(
        properties =
                "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class AssessmentModuleIntegrationTest {
    @MockitoBean AccountAccess accounts;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean PlatformTransactionManager transactionManager;
    @MockitoBean DataSource dataSource;

    @Test
    void moduleBootstrapsInIsolation() {}
}
