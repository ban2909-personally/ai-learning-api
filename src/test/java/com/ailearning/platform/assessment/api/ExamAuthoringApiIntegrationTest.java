package com.ailearning.platform.assessment.api;

import static org.assertj.core.api.Assertions.*;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ExamAuthoringApiIntegrationTest {
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

    private final UUID author = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @BeforeEach
    void users() {
        user(author, "LECTURE");
        user(other, "LECTURE");
        user(leader, "LEADER");
        user(student, "STUDENT");
    }

    private void user(UUID id, String role) {
        jdbc.update(
                "INSERT INTO users(id,email,password_hash,display_name,status) VALUES"
                        + " (?,?,?,'Staff','ACTIVE')",
                id,
                id + "@example.invalid",
                "unused");
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                id,
                role);
    }

    private RequestPostProcessor as(UUID id, String role) {
        return jwt().jwt(token -> token.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String create() throws Exception {
        String response =
                mvc.perform(
                                post("/api/v1/practice/authoring/exams")
                                        .with(as(author, "LECTURE"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
{"slug":"english-email","title":"English email","description":"","durationMinutes":20}
"""))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String save(String id, long version, String key) throws Exception {
        return mvc.perform(
                        put("/api/v1/practice/authoring/exams/" + id)
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"expectedVersion":%d,"title":"English email","description":"","durationMinutes":20,
 "sections":[{"skill":"READING","title":"Read an email","passage":"The meeting is Tuesday.",
     "audioText":null,"questions":[{"kind":"CHOICE","prompt":"When is the meeting?",
     "options":["Monday","Tuesday"],"correctAnswer":"%s","explanation":"Read the email."}]},
   {"skill":"WRITING","title":"Reply","passage":"Your colleague needs help.","audioText":null,
     "questions":[{"kind":"WRITING","prompt":"Write a helpful reply.","options":[],
        "correctAnswer":null,"explanation":""}]}]}
"""
                                                .formatted(version, key)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String transition(String id, String action, long version, UUID actor, String role)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/practice/authoring/exams/" + id + "/" + action)
                                .with(as(actor, role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void completeWorkflowKeepsOldResultsWhenNewRevisionChangesTheKey() throws Exception {
        String id = create();
        String saved = save(id, 0, "Tuesday");
        String question = JsonPath.read(saved, "$.exam.sections[0].questions[0].id");
        mvc.perform(get("/api/v1/practice/exams/english-email")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/practice/authoring/exams/" + id).with(as(other, "LECTURE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/practice/authoring/exams/" + id).with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/practice/authoring/exams/" + id + "/submit")
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        transition(id, "submit", 1, author, "LECTURE");
        mvc.perform(
                        post("/api/v1/practice/authoring/exams/" + id + "/publish")
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":2}"))
                .andExpect(status().isForbidden());
        transition(id, "publish", 2, leader, "LEADER");
        mvc.perform(get("/api/v1/practice/exams/english-email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].questions[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.sections[0].questions[0].explanation").doesNotExist());

        String response =
                mvc.perform(
                                post("/api/v1/practice/exams/english-email/attempts")
                                        .with(as(student, "STUDENT")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String attempt = JsonPath.read(response, "$.id");
        mvc.perform(
                        put("/api/v1/practice/attempts/" + attempt + "/answers/" + question)
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"Tuesday\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/practice/attempts/" + attempt + "/submit")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(1));

        String cloned =
                mvc.perform(
                                post("/api/v1/practice/authoring/exams/" + id + "/clone")
                                        .with(as(author, "LECTURE"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"expectedVersion\":3}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String newId = JsonPath.read(cloned, "$.id");
        String clonedQuestion = JsonPath.read(cloned, "$.exam.sections[0].questions[0].id");
        assertThat(clonedQuestion).isNotEqualTo(question);
        mvc.perform(
                        post("/api/v1/practice/authoring/exams/" + id + "/clone")
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":3}"))
                .andExpect(status().isConflict());
        save(newId, 0, "Monday");
        transition(newId, "submit", 1, author, "LECTURE");
        transition(newId, "publish", 2, leader, "LEADER");
        mvc.perform(
                        get("/api/v1/practice/attempts/" + attempt + "/result")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(1))
                .andExpect(jsonPath("$.sections[0].questions[0].correctAnswer").value("Tuesday"));
        mvc.perform(get("/api/v1/practice/attempts/" + attempt).with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exam.sections[0].questions[0].id").value(question));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM practice_exams WHERE id=?",
                                String.class,
                                UUID.fromString(id)))
                .isEqualTo("ARCHIVED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM practice_exams WHERE status='PUBLISHED' AND"
                                    + " series_id=(SELECT series_id FROM practice_exams WHERE"
                                    + " id=?)",
                                Integer.class,
                                UUID.fromString(id)))
                .isEqualTo(1);
    }

    @Test
    void newExamWritingReviewIsRestrictedToAuthorOrLeader() throws Exception {
        String id = create();
        String saved = save(id, 0, "Tuesday");
        String question = JsonPath.read(saved, "$.exam.sections[1].questions[0].id");
        transition(id, "submit", 1, author, "LECTURE");
        transition(id, "publish", 2, leader, "LEADER");
        String response =
                mvc.perform(
                                post("/api/v1/practice/exams/english-email/attempts")
                                        .with(as(student, "STUDENT")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String attempt = JsonPath.read(response, "$.id");
        mvc.perform(
                        put("/api/v1/practice/attempts/" + attempt + "/answers/" + question)
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"answer\":\"Thank you for your email. I am happy to"
                                            + " help.\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/practice/attempts/" + attempt + "/submit")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/practice/reviews/pending").with(as(other, "LECTURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/practice/reviews/pending").with(as(author, "LECTURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/practice/reviews/pending").with(as(leader, "LEADER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        String review =
                """
{"taskScore":4,"coherenceScore":4,"vocabularyScore":4,"grammarScore":4,"feedback":"Useful reply."}
""";
        mvc.perform(
                        put("/api/v1/practice/attempts/"
                                        + attempt
                                        + "/writing/"
                                        + question
                                        + "/review")
                                .with(as(other, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(review))
                .andExpect(status().isConflict());
        mvc.perform(
                        put("/api/v1/practice/attempts/"
                                        + attempt
                                        + "/writing/"
                                        + question
                                        + "/review")
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(review))
                .andExpect(status().isOk());
    }

    @Test
    void leaderCannotSeePrivateDraftAndOwnerCanWithdrawForCorrection() throws Exception {
        String id = create();
        mvc.perform(get("/api/v1/practice/authoring/exams/" + id).with(as(leader, "LEADER")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/practice/authoring/exams/" + id + "/submit")
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        save(id, 0, "Tuesday");
        transition(id, "submit", 1, author, "LECTURE");
        mvc.perform(get("/api/v1/practice/authoring/exams/" + id).with(as(leader, "LEADER")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.exam.sections[0].questions[0].correctAnswer").value("Tuesday"));
        transition(id, "withdraw", 2, author, "LECTURE");
        mvc.perform(get("/api/v1/practice/authoring/exams/" + id).with(as(author, "LECTURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void validationRejectsMissingVersionAndGuestsBeforeAnyMutation() throws Exception {
        String id = create();
        mvc.perform(
                        post("/api/v1/practice/authoring/exams/" + id + "/submit")
                                .with(as(author, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/practice/authoring/exams").with(as(UUID.randomUUID(), "GUEST")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/practice/authoring/exams")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/practice/authoring/exams")
                                .with(as(author, "LECTURE"))
                                .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
