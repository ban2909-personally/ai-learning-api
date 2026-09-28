package com.ailearning.platform.community.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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

@SpringBootTest(properties = "app.community.retention.enabled=false")
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
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired com.ailearning.platform.platform.security.SecurityProperties security;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.ailearning.platform.community.application.port.out.CommunityMediaStorage mediaStorage;

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
        when(mediaStorage.store(anyString(), anyString(), anyLong(), any()))
                .thenReturn("test-etag");
        when(mediaStorage.open(anyString(), anyLong(), anyLong()))
                .thenAnswer(
                        call ->
                                new java.io.ByteArrayInputStream(
                                        new byte
                                                [Math.toIntExact(
                                                        call.getArgument(2, Long.class))]));
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

    @Test
    void memberPostRequiresSpaceManagerApprovalAndCannotBeUsedBeforeApproval() throws Exception {
        String spaceId = createSpace(guest, "GUEST", "PAGE", "PUBLIC");
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/join")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("ACTIVE"));
        String postId = createPost(student, "STUDENT", "An English learning tip", spaceId);
        mvc.perform(get("/api/v1/community/posts/" + postId).with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/v1/community/posts/" + postId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/community/feed").param("spaceId", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(0));
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/community/posts/" + postId + "/comments")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/community/posts")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"\",\"sharedPostId\":\"" + postId + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/posts/pending")
                                .with(as(student, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/posts/pending")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId));
        mvc.perform(
                        post("/api/v1/community/spaces/"
                                        + spaceId
                                        + "/posts/"
                                        + postId
                                        + "/approve")
                                .with(as(student, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/spaces/"
                                        + spaceId
                                        + "/posts/"
                                        + postId
                                        + "/approve")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/community/feed").param("spaceId", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].id").value(postId));
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/posts/" + postId + "/reject")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isConflict());
    }

    @Test
    void revokedAuthorsCannotBeApprovedAndManagersCanRejectTheirPendingPost() throws Exception {
        String spaceId = createSpace(guest, "GUEST", "GROUP", "PUBLIC");
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/join")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        String postId = createPost(student, "STUDENT", "Review me", spaceId);
        String otherSpace = createSpace(guest, "GUEST", "GROUP", "PUBLIC");
        mvc.perform(
                        post("/api/v1/community/spaces/"
                                        + otherSpace
                                        + "/posts/"
                                        + postId
                                        + "/approve")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isConflict());
        mvc.perform(
                        delete("/api/v1/community/spaces/" + spaceId + "/members/" + student)
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/community/spaces/"
                                        + spaceId
                                        + "/posts/"
                                        + postId
                                        + "/approve")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/posts/" + postId + "/reject")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/community/posts/" + postId)).andExpect(status().isNotFound());
    }

    @Test
    void privatePageBlocksPendingMembersFromAllContentAndInteraction() throws Exception {
        String pageId = createSpace(guest, "GUEST", "PAGE", "PRIVATE");
        String postId = createPost(guest, "GUEST", "Private English discussion", pageId);
        mvc.perform(
                        post("/api/v1/community/spaces/" + pageId + "/join")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("PENDING"));
        mvc.perform(
                        get("/api/v1/community/feed")
                                .param("spaceId", pageId)
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/community/posts/" + postId + "/comments")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/comments")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"No access\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewsContainOnlyTwoActiveTopLevelCommentsAndCountersExcludeRemovedComments()
            throws Exception {
        String postId = createPost(guest, "GUEST", "English tips", null);
        String firstId = null;
        for (int index = 0; index < 4; index++) {
            String result =
                    mvc.perform(
                                    post("/api/v1/community/posts/" + postId + "/comments")
                                            .with(as(student, "STUDENT"))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"body\":\"Tip " + index + "\"}"))
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            if (index == 0) firstId = JsonPath.read(result, "$.id");
        }
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/comments")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"Reply\",\"parentId\":\"" + firstId + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/community/comments/" + firstId).with(as(student, "STUDENT")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/community/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].commentCount").value(4))
                .andExpect(jsonPath("$.posts[0].commentPreview.length()").value(2))
                .andExpect(jsonPath("$.posts[0].commentPreview[0].body").value("Tip 2"))
                .andExpect(jsonPath("$.posts[0].commentPreview[1].body").value("Tip 3"));
        // Like responses replace a card in the UI and must retain its preview.
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.commentPreview.length()").value(2))
                .andExpect(jsonPath("$.commentPreview[1].body").value("Tip 3"));
    }

    @Autowired com.ailearning.platform.community.api.usecase.MediaRetentionUseCase retention;

    @Test
    void expiredMediaIsHiddenBeforeCleanupAndOnlyItsCommunityObjectIsDeleted() throws Exception {
        String result =
                mvc.perform(
                                multipart("/api/v1/community/posts/media")
                                        .file(image())
                                        .with(as(guest, "GUEST"))
                                        .param("body", "Expires in fourteen days"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String postId = JsonPath.read(result, "$.id");
        String mediaId = JsonPath.read(result, "$.media.id");
        String shared = createSharedPost(guest, postId);
        jdbc.update(
                "UPDATE community_media_assets SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second'"
                    + " WHERE id=?::uuid",
                mediaId);
        mvc.perform(get("/api/v1/community/posts/" + postId)).andExpect(status().isNotFound());
        mvc.perform(head("/api/v1/media/community/posts/" + postId))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/community/posts/" + postId + "/likes")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/community/posts/" + shared))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedBody").doesNotExist())
                .andExpect(jsonPath("$.sharedMedia").doesNotExist());
        var cleanup = retention.cleanup();
        org.assertj.core.api.Assertions.assertThat(cleanup.deleted()).isEqualTo(1);
        verify(mediaStorage).delete("community/" + mediaId);
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM community_posts WHERE id=?::uuid",
                                String.class,
                                postId))
                .isEqualTo("REMOVED");
        org.assertj.core.api.Assertions.assertThat(retention.cleanup().claimed()).isZero();
    }

    @Test
    void failedRetentionDeletionHasDurableLeaseAndRetryWithoutExposingPost() throws Exception {
        String result =
                mvc.perform(
                                multipart("/api/v1/community/posts/media")
                                        .file(image())
                                        .with(as(guest, "GUEST")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = JsonPath.read(result, "$.media.id");
        jdbc.update(
                "UPDATE community_media_assets SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 day'"
                    + " WHERE id=?::uuid",
                id);
        doThrow(new IllegalStateException("offline")).when(mediaStorage).delete("community/" + id);
        org.assertj.core.api.Assertions.assertThat(retention.cleanup().failed()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(retention.cleanup().claimed()).isZero();
        jdbc.update(
                "UPDATE community_media_assets SET cleanup_claimed_until=CURRENT_TIMESTAMP-INTERVAL"
                    + " '1 second' WHERE id=?::uuid",
                id);
        doNothing().when(mediaStorage).delete("community/" + id);
        org.assertj.core.api.Assertions.assertThat(retention.cleanup().deleted()).isEqualTo(1);
    }

    private String createSharedPost(UUID actor, String original) throws Exception {
        var response =
                mvc.perform(
                                post("/api/v1/community/posts")
                                        .with(as(actor, "GUEST"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"body\":\"Shared routine\",\"sharedPostId\":\""
                                                        + original
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private org.springframework.mock.web.MockMultipartFile image() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                java.util.Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg=="));
    }

    private jakarta.servlet.http.Cookie mediaCookie(UUID actor, long expiresIn) {
        var now = java.time.Instant.now();
        var claims =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .issuer(security.issuer())
                        .subject(actor.toString())
                        .issuedAt(now.minusSeconds(600))
                        .expiresAt(now.plusSeconds(expiresIn))
                        .claim("roles", java.util.List.of("GUEST"))
                        .build();
        String value =
                encoder.encode(
                                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                                        org.springframework.security.oauth2.jwt.JwsHeader.with(
                                                        org.springframework.security.oauth2.jose.jws
                                                                .MacAlgorithm.HS256)
                                                .build(),
                                        claims))
                        .getTokenValue();
        return new jakarta.servlet.http.Cookie("media_access", value);
    }

    @Test
    void mediaIsOwnedByItsPostAndRequiresPrivateMembershipEvenForHeadAndRange() throws Exception {
        String spaceId = createSpace(student, "STUDENT", "GROUP", "PRIVATE");
        String result =
                mvc.perform(
                                multipart("/api/v1/community/posts/media")
                                        .file(image())
                                        .param("spaceId", spaceId)
                                        .with(as(student, "STUDENT")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.media.contentType").value("image/png"))
                        .andExpect(jsonPath("$.body").value(""))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String postId = JsonPath.read(result, "$.id");
        mvc.perform(get("/api/v1/media/community/posts/" + postId))
                .andExpect(status().isForbidden());
        mvc.perform(head("/api/v1/media/community/posts/" + postId))
                .andExpect(status().isForbidden());
        verify(mediaStorage, never()).open(anyString(), anyLong(), anyLong());
        mvc.perform(head("/api/v1/media/community/posts/" + postId).with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        verify(mediaStorage, never()).open(anyString(), anyLong(), anyLong());
        var stream =
                mvc.perform(
                                get("/api/v1/media/community/posts/" + postId)
                                        .header("Range", "bytes=2-5")
                                        .with(as(student, "STUDENT")))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(stream))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Length", "4"));
        mvc.perform(
                        get("/api/v1/media/community/posts/" + postId)
                                .header("Range", "bytes=999-1000")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(
                        get("/api/v1/media/community/posts/" + postId)
                                .header("Range", "bytes=0-1,3-4")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(delete("/api/v1/community/posts/" + postId).with(as(student, "STUDENT")))
                .andExpect(status().isNoContent());
        mvc.perform(head("/api/v1/media/community/posts/" + postId).with(as(student, "STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedGuestCanUploadAndCookieAuthenticatesOnlyMediaReadsNotMutations()
            throws Exception {
        String result =
                mvc.perform(
                                multipart("/api/v1/community/posts/media")
                                        .file(image())
                                        .with(as(guest, "GUEST")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String postId = JsonPath.read(result, "$.id");
        mvc.perform(head("/api/v1/media/community/posts/" + postId)).andExpect(status().isOk());
        String privateSpace = createSpace(guest, "GUEST", "PAGE", "PRIVATE");
        String privateResult =
                mvc.perform(
                                multipart("/api/v1/community/posts/media")
                                        .file(image())
                                        .param("spaceId", privateSpace)
                                        .with(as(guest, "GUEST")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String privatePost = JsonPath.read(privateResult, "$.id");
        mvc.perform(
                        head("/api/v1/media/community/posts/" + privatePost)
                                .cookie(mediaCookie(guest, 300)))
                .andExpect(status().isOk());
        mvc.perform(head("/api/v1/media/community/posts/" + privatePost))
                .andExpect(status().isForbidden());
        mvc.perform(
                        head("/api/v1/media/community/posts/" + postId)
                                .cookie(mediaCookie(guest, -120)))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        multipart("/api/v1/community/posts/media")
                                .file(image())
                                .cookie(mediaCookie(guest, 300)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingMediaCannotBeReadByOutsidersAndWrongContentTypeIsRejected() throws Exception {
        String spaceId = createSpace(student, "STUDENT", "GROUP", "PUBLIC");
        mvc.perform(post("/api/v1/community/spaces/" + spaceId + "/join").with(as(guest, "GUEST")))
                .andExpect(status().isOk());
        String result =
                mvc.perform(
                                multipart("/api/v1/community/posts/media")
                                        .file(image())
                                        .param("spaceId", spaceId)
                                        .with(as(guest, "GUEST")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String postId = JsonPath.read(result, "$.id");
        mvc.perform(head("/api/v1/media/community/posts/" + postId))
                .andExpect(status().isNotFound());
        mvc.perform(head("/api/v1/media/community/posts/" + postId).with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(
                        multipart("/api/v1/community/posts/media")
                                .file(
                                        new org.springframework.mock.web.MockMultipartFile(
                                                "file",
                                                "fake.png",
                                                "image/png",
                                                "<html>x</html>"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)))
                                .with(as(guest, "GUEST")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatIsMembersOnlyIdempotentAndModeratedWithinItsSpace() throws Exception {
        String spaceId = createSpace(guest, "GUEST", "GROUP", "PUBLIC");
        mvc.perform(get("/api/v1/community/spaces/" + spaceId + "/chat"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/join")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        String clientId = UUID.randomUUID().toString();
        String payload = "{\"clientId\":\"" + clientId + "\",\"body\":\"English chat\"}";
        String result =
                mvc.perform(
                                post("/api/v1/community/spaces/" + spaceId + "/chat")
                                        .with(as(student, "STUDENT"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String messageId = JsonPath.read(result, "$.id");
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(messageId));
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload.replace("English chat", "Changed")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/community/spaces/" + spaceId + "/chat").with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1));
        String otherSpace = createSpace(lecturer, "LECTURE", "GROUP", "PUBLIC");
        mvc.perform(
                        delete("/api/v1/community/spaces/" + otherSpace + "/chat/" + messageId)
                                .with(as(lecturer, "LECTURE")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/v1/community/spaces/" + spaceId + "/chat/" + messageId)
                                .with(as(guest, "GUEST")))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value(""))
                .andExpect(jsonPath("$.messages[0].removed").value(true));
        mvc.perform(
                        delete("/api/v1/community/spaces/" + spaceId + "/members/" + student)
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void privatePendingChatIsHiddenAndHistoryUsesBoundedKeysetPagination() throws Exception {
        String spaceId = createSpace(guest, "GUEST", "PAGE", "PRIVATE");
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/join")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/spaces/" + spaceId + "/chat")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"body\":\"No access\"}"))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 55; i++)
            jdbc.update(
                    "INSERT INTO community_space_messages(id,space_id,author_id,client_id,body)"
                            + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    UUID.fromString(spaceId),
                    guest,
                    UUID.randomUUID(),
                    "Message " + i);
        String result =
                mvc.perform(
                                get("/api/v1/community/spaces/" + spaceId + "/chat")
                                        .with(as(guest, "GUEST")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.messages.length()").value(50))
                        .andExpect(jsonPath("$.hasMore").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Number oldest = JsonPath.read(result, "$.oldestSequence");
        Number newest = JsonPath.read(result, "$.newestSequence");
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .param("before", oldest.toString())
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(5))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .param("after", newest.toString())
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(
                        get("/api/v1/community/spaces/" + spaceId + "/chat")
                                .param("after", "1")
                                .param("before", "2")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isBadRequest());
    }
}
