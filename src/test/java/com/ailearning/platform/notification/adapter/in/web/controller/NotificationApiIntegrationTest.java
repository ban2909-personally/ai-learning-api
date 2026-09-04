package com.ailearning.platform.notification.adapter.in.web.controller;

import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/notification-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/notification-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class NotificationApiIntegrationTest {
    private static final String FIRST_USER = "8ec33d91-0cc4-445f-9266-5f44d7bca900";
    private static final String SECOND_USER = "bfbc9cf4-5b2c-4db0-8728-27c65a99bb13";
    private static final String NEWEST = "2ee6684d-ebda-4b8c-8fd3-cf6a8f0ff101";
    private static final String OLDEST = "2ee6684d-ebda-4b8c-8fd3-cf6a8f0ff102";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_notification_api_test")
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

    @MockitoBean
    private NotificationRealtimeDelivery realtime;

    @Test
    void requiresAuthenticationForHistoryAndReadState() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/me/notifications/{notificationId}/read", NEWEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersBoundedHistory() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("limit", "1")
                        .with(jwt().jwt(token -> token.subject(FIRST_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(NEWEST))
                .andExpect(jsonPath("$.content[0].title").value("Thông báo mới nhất"))
                .andExpect(jsonPath("$.nextCursor").value(NEWEST))
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("limit", "1")
                        .param("before", NEWEST)
                        .with(jwt().jwt(token -> token.subject(FIRST_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(OLDEST))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void marksAnOwnedNotificationReadButHidesAnotherUsersRow() throws Exception {
        mockMvc.perform(patch("/api/v1/me/notifications/{notificationId}/read", NEWEST)
                        .with(jwt().jwt(token -> token.subject(SECOND_USER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("notification_not_found"));

        mockMvc.perform(patch("/api/v1/me/notifications/{notificationId}/read", NEWEST)
                        .with(jwt().jwt(token -> token.subject(FIRST_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NEWEST))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    void rejectsAnUnboundedPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications")
                        .param("limit", "51")
                        .with(jwt().jwt(token -> token.subject(FIRST_USER))))
                .andExpect(status().isBadRequest());
    }
}
