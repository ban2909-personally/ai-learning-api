package com.ailearning.platform.community.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CommunityApiIntegrationTest {
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

    private final UUID guest = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private final UUID student = UUID.fromString("31000000-0000-0000-0000-000000000002");
    private final UUID lecturer = UUID.fromString("31000000-0000-0000-0000-000000000003");

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE users CASCADE");
        addUser(guest, "GUEST", "guest@community.test");
        addUser(student, "STUDENT", "student@community.test");
        addUser(lecturer, "LECTURE", "lecture@community.test");
    }

    private void addUser(UUID id, String role, String email) {
        jdbc.update(
                "INSERT INTO users(id,email,password_hash,display_name,status)"
                        + " VALUES (?,?,?,'Community Member','ACTIVE')",
                id,
                email,
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

    private String createSpace(UUID owner, String role, String kind, String visibility)
            throws Exception {
        String response =
                mvc.perform(
                                post("/api/v1/community/spaces")
                                        .with(as(owner, role))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\"Java Study"
                                                    + " Club\",\"description\":\"Learn together\","
                                                    + "\"kind\":\""
                                                        + kind
                                                        + "\",\"visibility\":\""
                                                        + visibility
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.myRole").value("OWNER"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String createPost(UUID author, String role, String body, String spaceId)
            throws Exception {
        String payload =
                "{\"body\":\""
                        + body
                        + "\""
                        + (spaceId == null ? "" : ",\"spaceId\":\"" + spaceId + "\"")
                        + "}";
        String response =
                mvc.perform(
                                post("/api/v1/community/posts")
                                        .with(as(author, role))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void authenticatedGuestCanPostReactReplyAndShareWhileAnonymousCanRead() throws Exception {
        mvc.perform(get("/api/v1/community/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0));
        mvc.perform(
                        post("/api/v1/community/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());

        String postId = createPost(guest, "GUEST", "Java tip", null);
        mvc.perform(get("/api/v1/community/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].body").value("Java tip"));
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        String comment =
                mvc.perform(
                                post("/api/v1/community/posts/" + postId + "/comments")
                                        .with(as(student, "STUDENT"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"body\":\"Useful!\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String commentId = JsonPath.read(comment, "$.id");
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/comments")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"body\":\"Thanks\",\"parentId\":\"" + commentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId));
        mvc.perform(get("/api/v1/community/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(
                        post("/api/v1/community/posts")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"body\":\"Read this too\",\"sharedPostId\":\""
                                                + postId
                                                + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharedBody").value("Java tip"));
        mvc.perform(
                        delete("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    void privateGroupHidesFeedUntilApprovalAndNeverAllowsPublicShare() throws Exception {
        String groupId = createSpace(student, "STUDENT", "GROUP", "PRIVATE");
        String postId = createPost(student, "STUDENT", "Private lesson", groupId);
        mvc.perform(get("/api/v1/community/feed").param("spaceId", groupId))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/community/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0));
        mvc.perform(post("/api/v1/community/spaces/" + groupId + "/join").with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("PENDING"));
        mvc.perform(get("/api/v1/community/posts/" + postId).with(as(guest, "GUEST")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/spaces/"
                                        + groupId
                                        + "/members/"
                                        + guest
                                        + "/approve")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/community/feed")
                                .with(as(guest, "GUEST"))
                                .param("spaceId", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].body").value("Private lesson"));
        mvc.perform(
                        post("/api/v1/community/posts")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"body\":\"Leaked?\",\"sharedPostId\":\""
                                                + postId
                                                + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/v1/community/spaces/" + groupId + "/members/" + student)
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pageOwnerControlsPublishingInvitationsAndMemberRoles() throws Exception {
        String pageId = createSpace(guest, "GUEST", "PAGE", "PUBLIC");
        mvc.perform(
                        post("/api/v1/community/posts")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"body\":\"Not allowed\",\"spaceId\":\""
                                                + pageId
                                                + "\"}"))
                .andExpect(status().isForbidden());
        createPost(guest, "GUEST", "Welcome to the page", pageId);
        mvc.perform(
                        post("/api/v1/community/spaces/" + pageId + "/invite")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"lecture@community.test\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/community/spaces/" + pageId + "/join")
                                .with(as(lecturer, "LECTURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("ACTIVE"));
        mvc.perform(
                        patch(
                                        "/api/v1/community/spaces/"
                                                + pageId
                                                + "/members/"
                                                + lecturer
                                                + "/role")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        createPost(lecturer, "LECTURE", "Admin contribution", pageId);
        mvc.perform(
                        get("/api/v1/community/spaces/" + pageId + "/members")
                                .with(as(lecturer, "LECTURE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(
                        delete("/api/v1/community/spaces/" + pageId + "/members/" + guest)
                                .with(as(lecturer, "LECTURE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void feedCursorDoesNotRepeatBoundaryPost() throws Exception {
        for (int index = 0; index < 5; index++) createPost(guest, "GUEST", "Note " + index, null);
        String page =
                mvc.perform(get("/api/v1/community/feed").param("size", "2"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.posts.length()").value(2))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String cursor = JsonPath.read(page, "$.nextCursor");
        String firstId = JsonPath.read(page, "$.posts[0].id");
        mvc.perform(get("/api/v1/community/feed").param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(org.hamcrest.Matchers.not(firstId)));
    }
}
