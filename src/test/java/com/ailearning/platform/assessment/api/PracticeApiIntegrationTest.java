package com.ailearning.platform.assessment.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PracticeApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.ailearning.platform.catalog.application.port.out.PopularCatalogCache catalogCache;

    private final UUID student = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private final UUID other = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private final UUID lecturer = UUID.fromString("41000000-0000-0000-0000-000000000003");

    @BeforeEach
    void users() {
        jdbc.execute("TRUNCATE practice_attempts CASCADE");
        jdbc.execute("DELETE FROM user_roles");
        jdbc.execute("DELETE FROM users");
        for (UUID id : new UUID[] {student, other, lecturer}) {
            jdbc.update(
                    "INSERT INTO users(id,email,password_hash,display_name,status) VALUES"
                            + " (?,?,?,'Student','ACTIVE')",
                    id,
                    id + "@example.invalid",
                    "unused");
            jdbc.update(
                    "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                    id,
                    id.equals(lecturer) ? "LECTURE" : "STUDENT");
        }
    }

    private RequestPostProcessor as(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()))
                .authorities(
                        new SimpleGrantedAuthority(
                                id.equals(lecturer) ? "ROLE_LECTURE" : "ROLE_STUDENT"));
    }

    @Test
    void publicExamNeverLeaksKeyOrExplanation() throws Exception {
        mvc.perform(get("/api/v1/practice/exams/english-workplace-starter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].questions[0].prompt").exists())
                .andExpect(jsonPath("$.sections[0].questions[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.sections[0].questions[0].explanation").doesNotExist());
        mvc.perform(post("/api/v1/practice/exams/english-workplace-starter/attempts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanSubmitAndReviewButOthersCannotAccessOrModify() throws Exception {
        String response =
                mvc.perform(
                                post("/api/v1/practice/exams/english-workplace-starter/attempts")
                                        .with(as(student)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String attemptId = JsonPath.read(response, "$.id");
        String choiceId = "33333333-3333-4333-8333-333333333331";
        String writingId = "33333333-3333-4333-8333-333333333335";

        mvc.perform(get("/api/v1/practice/attempts/" + attemptId).with(as(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/practice/attempts/" + attemptId + "/result").with(as(student)))
                .andExpect(status().isConflict());
        mvc.perform(
                        put("/api/v1/practice/attempts/" + attemptId + "/answers/" + choiceId)
                                .with(as(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"Tuesday at 9:30\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/v1/practice/attempts/" + attemptId + "/answers/" + writingId)
                                .with(as(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"answer\":\"I suggest a workshop about useful meeting"
                                            + " phrases.\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/practice/attempts/" + attemptId + "/submit").with(as(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(1))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.sections[2].questions[0].status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/v1/practice/attempts/" + attemptId + "/result").with(as(other)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put("/api/v1/practice/attempts/" + attemptId + "/answers/" + choiceId)
                                .with(as(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"Monday at 9:30\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void authenticatedGuestCanDiscoverButCannotParticipateOrReview() throws Exception {
        RequestPostProcessor guest =
                jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
        mvc.perform(get("/api/v1/practice/exams/english-workplace-starter").with(guest))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/practice/exams/english-workplace-starter/attempts").with(guest))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/practice/attempts/" + UUID.randomUUID()).with(guest))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/practice/reviews/pending").with(guest))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyReviewerCanGradeSubmittedWritingAndStudentSeesRubric() throws Exception {
        String response =
                mvc.perform(
                                post("/api/v1/practice/exams/english-workplace-starter/attempts")
                                        .with(as(student)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String attemptId = JsonPath.read(response, "$.id");
        String writingId = "33333333-3333-4333-8333-333333333335";
        mvc.perform(
                        put("/api/v1/practice/attempts/" + attemptId + "/answers/" + writingId)
                                .with(as(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"answer\":\"I suggest a workshop about clear workplace"
                                            + " emails.\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/practice/attempts/" + attemptId + "/submit").with(as(student)))
                .andExpect(status().isOk());

        String ownResponse =
                mvc.perform(
                                post("/api/v1/practice/exams/english-workplace-starter/attempts")
                                        .with(as(lecturer)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String ownAttemptId = JsonPath.read(ownResponse, "$.id");
        mvc.perform(
                        put("/api/v1/practice/attempts/" + ownAttemptId + "/answers/" + writingId)
                                .with(as(lecturer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"My own writing answer.\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/practice/attempts/" + ownAttemptId + "/submit")
                                .with(as(lecturer)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/practice/reviews/pending").with(as(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/practice/reviews/pending").with(as(lecturer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].attemptId").value(attemptId));
        String review =
                """
                {"taskScore":4,"coherenceScore":3,"vocabularyScore":4,
                 "grammarScore":5,"feedback":"Clear purpose; improve transitions."}
                """;
        mvc.perform(
                        put("/api/v1/practice/attempts/"
                                        + attemptId
                                        + "/writing/"
                                        + writingId
                                        + "/review")
                                .with(as(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(review))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/practice/attempts/"
                                        + attemptId
                                        + "/writing/"
                                        + writingId
                                        + "/review")
                                .with(as(lecturer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("Clear purpose; improve transitions."));
        mvc.perform(get("/api/v1/practice/attempts/" + attemptId + "/result").with(as(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[2].questions[0].status").value("REVIEWED"))
                .andExpect(
                        jsonPath("$.sections[2].questions[0].writingFeedback.totalScore")
                                .value(16));
        mvc.perform(
                        put("/api/v1/practice/attempts/"
                                        + attemptId
                                        + "/writing/"
                                        + writingId
                                        + "/review")
                                .with(as(lecturer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(review))
                .andExpect(status().isConflict());
    }
}
