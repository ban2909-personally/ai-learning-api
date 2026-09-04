package com.ailearning.platform.analytics.adapter.in.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/analytics-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/analytics-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LearningAnalyticsApiIntegrationTest {
    private static final String FIRST_USER = "8ec33d91-0cc4-445f-9266-5f44d7bca900";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_analytics_api_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/learning-analytics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyAuthenticatedUsersBoundedCompletionSummary() throws Exception {
        mockMvc.perform(get("/api/v1/me/learning-analytics")
                        .param("courseLimit", "1")
                        .with(jwt().jwt(token -> token.subject(FIRST_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessons").value(3))
                .andExpect(jsonPath("$.coursesWithCompletions").value(2))
                .andExpect(jsonPath("$.lastCompletedAt").value("2026-09-04T10:00:00Z"))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].courseId").value("8aff449f-cfa6-4ed8-a3e7-5461090ee101"))
                .andExpect(jsonPath("$.courses[0].completedLessons").value(2));
    }

    @Test
    void rejectsAnUnboundedCourseLimit() throws Exception {
        mockMvc.perform(get("/api/v1/me/learning-analytics")
                        .param("courseLimit", "101")
                        .with(jwt().jwt(token -> token.subject(FIRST_USER))))
                .andExpect(status().isBadRequest());
    }
}
