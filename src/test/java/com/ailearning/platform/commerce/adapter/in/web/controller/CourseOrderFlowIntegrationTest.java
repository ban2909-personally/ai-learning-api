package com.ailearning.platform.commerce.adapter.in.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/commerce-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/commerce-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CourseOrderFlowIntegrationTest {
    private static final String USER_ID = "8ec33d91-0cc4-445f-9266-5f44d7bca900";
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("b6ccac9b-fe4e-48f3-8cd2-81dbbeb4ad01");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_commerce_flow_test")
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

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsReplaysAndListsOneOrderThroughTheRealAdapters() throws Exception {
        var request = post("/api/v1/me/orders")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseSlug\":\"clean-architecture\"}")
                .with(jwt().jwt(token -> token.subject(USER_ID)));

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseSlug").value("clean-architecture"))
                .andExpect(jsonPath("$.amount").value(499000))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseTitle").value("Clean Architecture"));

        mockMvc.perform(get("/api/v1/me/orders")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseId").value("58d19684-f4dc-46a7-b716-8ba176e185f3"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_orders WHERE user_id = ?",
                Integer.class,
                UUID.fromString(USER_ID)
        )).isOne();
    }
}
