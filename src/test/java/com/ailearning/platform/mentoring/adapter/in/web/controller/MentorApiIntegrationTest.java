package com.ailearning.platform.mentoring.adapter.in.web.controller;

import com.ailearning.platform.mentoring.api.contract.MentorAnswerObserver;
import com.ailearning.platform.mentoring.api.contract.MentorMessageView;
import com.ailearning.platform.mentoring.api.contract.MentorTurnView;
import com.ailearning.platform.mentoring.api.usecase.MentorUseCase;
import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MentorApiIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("18111111-1111-4111-8111-111111111111");
    private static final UUID LESSON_ID = UUID.fromString("58555555-5555-4555-8555-555555555555");
    private static final String PATH = "/api/v1/me/courses/mentor-test-course/lessons/"
            + LESSON_ID + "/mentor/messages";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_mentor_api_test")
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

    @MockitoBean
    MentorUseCase mentor;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsConversationHistoryAsJson() throws Exception {
        when(mentor.history(USER_ID, "mentor-test-course", LESSON_ID)).thenReturn(List.of(
                message(UUID.randomUUID(), MentorMessageRole.ASSISTANT, "Try one small example.")
        ));

        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[0].content").value("Try one small example."));
    }

    @Test
    void rejectsOversizedQuestionBeforeStartingAStream() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void streamsNamedEventsWithoutExposingProviderDetails() throws Exception {
        MentorMessageView user = message(UUID.randomUUID(), MentorMessageRole.USER, "Where should I start?");
        MentorMessageView assistant = message(UUID.randomUUID(), MentorMessageRole.ASSISTANT, "Start small.");
        doAnswer(invocation -> {
            MentorAnswerObserver observer = invocation.getArgument(4);
            observer.accepted(user, 19);
            observer.delta("Start ");
            observer.delta("small.");
            MentorTurnView turn = new MentorTurnView(user, assistant, 19);
            observer.completed(turn);
            return turn;
        }).when(mentor).ask(any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(post(PATH)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"question\":\"Where should I start?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("event:message"),
                        org.hamcrest.Matchers.containsString("event:delta"),
                        org.hamcrest.Matchers.containsString("event:complete"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("gpt-test"))
                )));
    }

    private MentorMessageView message(UUID id, MentorMessageRole role, String content) {
        return new MentorMessageView(id, role, content, Instant.parse("2026-09-03T12:00:00Z"));
    }
}
