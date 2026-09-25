package com.ailearning.platform.identity.api;

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
class WorkspaceFlowIntegrationTest {
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

    private final UUID admin = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private final UUID lecturer = UUID.fromString("21000000-0000-0000-0000-000000000002");
    private final UUID leader = UUID.fromString("21000000-0000-0000-0000-000000000003");
    private final UUID student = UUID.fromString("21000000-0000-0000-0000-000000000004");

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE users CASCADE");
        addUser(admin, "ADMIN");
        addUser(lecturer, "LECTURE");
        addUser(leader, "LEADER");
        addUser(student, "STUDENT");
    }

    private void addUser(UUID id, String role) {
        jdbc.update(
                "INSERT INTO users(id,email,password_hash,display_name,status) VALUES"
                        + " (?,?,?,'Demo','ACTIVE')",
                id,
                id + "@example.invalid",
                "unused-test-hash");
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                id,
                role);
    }

    private RequestPostProcessor as(UUID id, String role) {
        return jwt().jwt(token -> token.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void accountAdministrationRequiresCurrentAdminAndProtectsLastAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/accounts").with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accounts/summary").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(4))
                .andExpect(jsonPath("$.ADMIN").value(1));
        mvc.perform(
                        patch("/api/v1/admin/accounts/" + admin)
                                .with(as(admin, "ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?", admin);
        mvc.perform(get("/api/v1/admin/accounts").with(as(admin, "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsStrongPasswordAccountButRejectsDemoPasswordThroughPublicAdministration()
            throws Exception {
        String payload =
                """
{"email":"new@example.invalid","displayName":"New Lecturer","password":"learning2026","role":"LECTURE"}
""";
        mvc.perform(
                        post("/api/v1/admin/accounts")
                                .with(as(admin, "ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("LECTURE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(
                        post("/api/v1/admin/accounts")
                                .with(as(admin, "ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload.replace("learning2026", "123456")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courseAuthoringChecksOwnershipAndReviewRole() throws Exception {
        String payload =
                """
{"categoryId":"a99d920d-87ae-40ea-aed0-678885c26bfa","slug":"course-workspace",
 "title":"Java","shortDescription":"Intro","description":"Learn Java","level":"BEGINNER","price":0}
""";
        mvc.perform(
                        post("/api/v1/instructor/courses")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isForbidden());
        String created =
                mvc.perform(
                                post("/api/v1/instructor/courses")
                                        .with(as(lecturer, "LECTURE"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(
                        post("/api/v1/instructor/courses/" + id + "/lessons")
                                .with(as(leader, "LEADER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sectionTitle\":\"Start\",\"title\":\"Hello\",\"contentUrl\":\"https://example.com/lesson\",\"durationSeconds\":60,\"preview\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/instructor/courses/" + id + "/lessons")
                                .with(as(lecturer, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sectionTitle\":\"Start\",\"title\":\"Hello\",\"contentUrl\":\"https://example.com/lesson\",\"durationSeconds\":60,\"preview\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(
                        patch("/api/v1/instructor/courses/" + id + "/status")
                                .with(as(lecturer, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PENDING_REVIEW\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        patch("/api/v1/instructor/courses/" + id + "/status")
                                .with(as(lecturer, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/v1/instructor/courses/" + id + "/status")
                                .with(as(leader, "LEADER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/instructor/courses/" + id).with(as(lecturer, "LECTURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.lessons[0].title").value("Hello"));
    }

    @Test
    void flashcardsArePrivateAndPersistEdits() throws Exception {
        String body =
                "{\"title\":\"Java\",\"description\":\"Basics\",\"cards\":[{\"front\":\"JVM?\",\"back\":\"Java"
                    + " Virtual Machine\"}]}";
        String created =
                mvc.perform(
                                post("/api/v1/me/flashcards")
                                        .with(as(student, "STUDENT"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/v1/me/flashcards/" + id).with(as(admin, "ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put("/api/v1/me/flashcards/" + id)
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.replace("Basics", "Updated")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/flashcards/" + id).with(as(student, "STUDENT")))
                .andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.cards.length()").value(1));
        mvc.perform(delete("/api/v1/me/flashcards/" + id).with(as(student, "STUDENT")))
                .andExpect(status().isNoContent());
    }

    @Test
    void guestHasNoPrivateLearningAccess() throws Exception {
        mvc.perform(get("/api/v1/me/flashcards").with(as(student, "GUEST")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accounts")).andExpect(status().isUnauthorized());
    }
}
