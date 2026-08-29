package com.ailearning.platform.learning.api;

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
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/learning-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/learning-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EnrollmentApiIntegrationTest {
    private static final String STUDENT_ID = "8ec33d91-0cc4-445f-9266-5f44d7bca900";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_enrollment_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/courses/java-free-learning-test/enrollments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enrollsFreeCourseIdempotentlyAndListsIt() throws Exception {
        String first = mockMvc.perform(post("/api/v1/courses/java-free-learning-test/enrollments")
                        .with(jwt().jwt(token -> token.subject(STUDENT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/courses/java-free-learning-test/enrollments")
                        .with(jwt().jwt(token -> token.subject(STUDENT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(equalTo(
                        com.jayway.jsonpath.JsonPath.read(first, "$.id")
                )));

        mockMvc.perform(get("/api/v1/me/enrollments")
                        .with(jwt().jwt(token -> token.subject(STUDENT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].course.slug").value("java-free-learning-test"));
    }

    @Test
    void rejectsDirectEnrollmentForPaidCourse() throws Exception {
        mockMvc.perform(post("/api/v1/courses/java-paid-learning-test/enrollments")
                        .with(jwt().jwt(token -> token.subject(STUDENT_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_required"));
    }
}
