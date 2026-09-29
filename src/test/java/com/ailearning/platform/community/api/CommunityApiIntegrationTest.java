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

    @Test
    void discoveryMatchesOneCharacterAccentsAndRanksPrefixWithoutExposingIdentitySecrets()
            throws Exception {
        jdbc.update("UPDATE users SET display_name='Hà Anh' WHERE id=?", guest);
        jdbc.update("UPDATE users SET display_name='Minh Hà' WHERE id=?", student);
        jdbc.update(
                "UPDATE users SET display_name='Hà Disabled',status='DISABLED' WHERE id=?",
                lecturer);
        String group = createSpace(guest, "GUEST", "GROUP", "PRIVATE");
        String page = createSpace(guest, "GUEST", "PAGE", "PUBLIC");
        jdbc.update(
                "UPDATE community_spaces SET name='Hội trí tuệ nhân tạo' WHERE id=?::uuid", group);
        jdbc.update("UPDATE community_spaces SET name='English Hub' WHERE id=?::uuid", page);
        mvc.perform(get("/api/v1/community/search").param("q", "h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaces.length()").value(2))
                .andExpect(jsonPath("$.people.length()").value(2));
        mvc.perform(get("/api/v1/community/search").param("q", "HA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].displayName").value("Hà Anh"))
                .andExpect(jsonPath("$.people[0].email").doesNotExist())
                .andExpect(jsonPath("$.people[0].roles").doesNotExist());
        mvc.perform(get("/api/v1/community/search").param("q", "tri tue"))
                .andExpect(jsonPath("$.spaces.length()").value(1))
                .andExpect(jsonPath("$.spaces[0].visibility").value("PRIVATE"));
        mvc.perform(get("/api/v1/community/people/" + lecturer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/community/people/" + guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Hà Anh"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void directoryTreatsWildcardCharactersLiterallyAndBoundsPagination() throws Exception {
        jdbc.update("UPDATE users SET display_name=? WHERE id=?", "100%_\\English", guest);
        String group = createSpace(guest, "GUEST", "GROUP", "PUBLIC");
        jdbc.update("UPDATE community_spaces SET name=? WHERE id=?::uuid", "100%_\\English", group);
        for (String query : java.util.List.of("%", "_", "\\", "100%_\\")) {
            mvc.perform(get("/api/v1/community/search").param("q", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.people.length()").value(1))
                    .andExpect(jsonPath("$.spaces.length()").value(1));
        }
        mvc.perform(get("/api/v1/community/search").param("q", "' OR true --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people").isEmpty());
        mvc.perform(get("/api/v1/community/search")).andExpect(jsonPath("$.people").isEmpty());
        mvc.perform(get("/api/v1/community/search").param("q", "h".repeat(121)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/community/search").param("q", "h").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/community/search").param("q", "h").param("page", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/community/people/not-a-uuid")).andExpect(status().isBadRequest());
        for (int index = 0; index < 21; index++) {
            addUser(UUID.randomUUID(), "STUDENT", "directory" + index + "@example.test");
        }
        mvc.perform(get("/api/v1/community/search").param("q", "Member"))
                .andExpect(jsonPath("$.people.length()").value(20))
                .andExpect(jsonPath("$.peopleHasMore").value(true));
        mvc.perform(get("/api/v1/community/search").param("q", "Member").param("page", "1"))
                .andExpect(jsonPath("$.people.length()").value(3))
                .andExpect(jsonPath("$.peopleHasMore").value(false));
    }

    @Test
    void publicProfileFeedCannotExposePrivatePostsEvenForOwnerAndKeepsAuthorFilterAcrossPages()
            throws Exception {
        String privateGroup = createSpace(guest, "GUEST", "GROUP", "PRIVATE");
        createPost(guest, "GUEST", "Private secret", privateGroup);
        createPost(guest, "GUEST", "Public first", null);
        createPost(guest, "GUEST", "Public second", null);
        createPost(student, "STUDENT", "Other author", null);
        String first =
                mvc.perform(
                                get("/api/v1/community/feed")
                                        .param("authorId", guest.toString())
                                        .param("size", "1")
                                        .with(as(guest, "GUEST")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.posts.length()").value(1))
                        .andExpect(jsonPath("$.posts[0].body").value("Public second"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");
        mvc.perform(
                        get("/api/v1/community/feed")
                                .param("authorId", guest.toString())
                                .param("cursor", cursor))
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].body").value("Public first"));
        mvc.perform(get("/api/v1/community/feed").param("authorId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

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

    private String discussion(UUID author, String role, String spaceId, String kind)
            throws Exception {
        String response =
                mvc.perform(
                                post("/api/v1/community/posts")
                                        .with(as(author, role))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
{"body":"","spaceId":%s,"features":{"appearance":{"attachmentUrl":"https://example.com/enroll","backgroundColor":"#173569","fontColor":"#ffffff"},"poll":{"kind":"%s","question":"Which skill?","options":["Listening","Reading"],"closesAt":null}}}
"""
                                                        .formatted(
                                                                spaceId == null
                                                                        ? "null"
                                                                        : "\"" + spaceId + "\"",
                                                                kind)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.poll.options.length()").value(2))
                        .andExpect(
                                jsonPath("$.appearance.attachmentUrl")
                                        .value("https://example.com/enroll"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return response;
    }

    @Test
    void pollsPersistSingleVoteAllowChangingRetractionAndAuthorClosure() throws Exception {
        String result = discussion(guest, "GUEST", null, "POLL");
        String id = JsonPath.read(result, "$.id"),
                first = JsonPath.read(result, "$.poll.options[0].id"),
                second = JsonPath.read(result, "$.poll.options[1].id");
        mvc.perform(get("/api/v1/community/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].poll.question").value("Which skill?"));
        String path = "/api/v1/community/posts/" + id + "/poll";
        mvc.perform(
                        post(path + "/votes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + first + "\"}"))
                .andExpect(status().isUnauthorized());
        for (int i = 0; i < 2; i++)
            mvc.perform(
                            post(path + "/votes")
                                    .with(as(student, "STUDENT"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"optionId\":\"" + first + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.poll.totalVotes").value(1))
                    .andExpect(jsonPath("$.poll.myOptionId").value(first));
        mvc.perform(
                        post(path + "/votes")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + second + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poll.options[0].votes").value(0))
                .andExpect(jsonPath("$.poll.options[1].votes").value(1));
        mvc.perform(
                        post(path + "/votes")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(path + "/votes").with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poll.totalVotes").value(0));
        mvc.perform(post(path + "/close").with(as(student, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/close").with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poll.closed").value(true));
        mvc.perform(
                        post(path + "/votes")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + first + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void pendingElectionKeepsOptionsInReviewAndPrivatePollRequiresApprovedMembership()
            throws Exception {
        String space = createSpace(guest, "GUEST", "PAGE", "PRIVATE");
        String owner = discussion(guest, "GUEST", space, "ELECTION");
        String ownerId = JsonPath.read(owner, "$.id"),
                option = JsonPath.read(owner, "$.poll.options[0].id");
        mvc.perform(
                        post("/api/v1/community/posts/" + ownerId + "/poll/votes")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + option + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/spaces/" + space + "/join")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/community/posts/" + ownerId).with(as(student, "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/community/spaces/"
                                        + space
                                        + "/members/"
                                        + student
                                        + "/approve")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk());
        String pending = discussion(student, "STUDENT", space, "ELECTION"),
                id = JsonPath.read(pending, "$.id");
        mvc.perform(
                        get("/api/v1/community/spaces/" + space + "/posts/pending")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].poll.kind").value("ELECTION"))
                .andExpect(jsonPath("$[0].poll.options.length()").value(2));
        mvc.perform(
                        post("/api/v1/community/posts/" + id + "/poll/votes")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + option + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/community/spaces/" + space + "/posts/" + id + "/approve")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/community/posts/" + ownerId + "/poll/votes")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"optionId\":\"" + option + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/community/posts/" + id + "/poll/close").with(as(guest, "GUEST")))
                .andExpect(status().isOk());
    }

    @Test
    void unsafeLinkLowContrastAndDuplicateOptionsCannotPersistPartialPost() throws Exception {
        for (String features :
                java.util.List.of(
                        "{\"appearance\":{\"attachmentUrl\":\"javascript:alert(1)\"}}",
                        "{\"appearance\":{\"backgroundColor\":\"#ffffff\",\"fontColor\":\"#ffffff\"}}",
                        "{\"poll\":{\"kind\":\"POLL\",\"question\":\"Skill?\",\"options\":[\"Reading\",\""
                            + " reading \"]}}")) {
            mvc.perform(
                            post("/api/v1/community/posts")
                                    .with(as(guest, "GUEST"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"body\":\"hello\",\"features\":" + features + "}"))
                    .andExpect(status().isBadRequest());
        }
        org.junit.jupiter.api.Assertions.assertEquals(
                0L, jdbc.queryForObject("SELECT count(*) FROM community_posts", Long.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                0L, jdbc.queryForObject("SELECT count(*) FROM community_polls", Long.class));
    }

    @Test
    void multipartMediaAcceptsFeaturesWithoutBreakingUploadContract() throws Exception {
        byte[] png =
                java.util.HexFormat.of()
                        .parseHex(
                                "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082");
        var file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "demo.png", "image/png", png);
        var features =
                new org.springframework.mock.web.MockMultipartFile(
                        "features",
                        "",
                        "application/json",
                        "{\"poll\":{\"kind\":\"POLL\",\"question\":\"Skill?\",\"options\":[\"Reading\",\"Listening\"]}}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(
                        multipart("/api/v1/community/posts/media")
                                .file(file)
                                .file(features)
                                .param("body", "hello")
                                .with(as(guest, "GUEST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poll.options.length()").value(2))
                .andExpect(jsonPath("$.media.contentType").value("image/png"))
                .andExpect(jsonPath("$.mediaExpiresAt").isNotEmpty());
    }

    private String directRequest(UUID actor, String role, String email, UUID client, String body)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/community/direct/conversations")
                                .with(as(actor, role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + email
                                                + "\",\"clientId\":\""
                                                + client
                                                + "\",\"body\":\""
                                                + body
                                                + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void privateChatRequiresRecipientConsentAndIdempotentSendWithHonestUnreadMarkers()
            throws Exception {
        UUID client = UUID.randomUUID();
        String request = directRequest(guest, "GUEST", "student@community.test", client, "Hello");
        String id = JsonPath.read(request, "$.id"),
                path = "/api/v1/community/direct/conversations/" + id;
        String duplicate = directRequest(guest, "GUEST", "student@community.test", client, "Hello");
        org.junit.jupiter.api.Assertions.assertEquals(id, JsonPath.read(duplicate, "$.id"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1L,
                jdbc.queryForObject("SELECT count(*) FROM community_direct_messages", Long.class));
        mvc.perform(get("/api/v1/community/direct/conversations"))
                .andExpect(status().isUnauthorized());
        for (String endpoint : java.util.List.of("/messages", "/read", "/decision")) {
            if (endpoint.equals("/messages"))
                mvc.perform(get(path + endpoint).with(as(lecturer, "ADMIN")))
                        .andExpect(status().isNotFound());
            else
                mvc.perform(
                                post(path + endpoint)
                                        .with(as(lecturer, "ADMIN"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                endpoint.equals("/read")
                                                        ? "{\"sequence\":1}"
                                                        : "{\"accept\":true}"))
                        .andExpect(status().isNotFound());
        }
        mvc.perform(
                        post(path + "/decision")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
        String send = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"body\":\"Waiting\"}";
        mvc.perform(
                        post(path + "/messages")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(send))
                .andExpect(status().isConflict());
        mvc.perform(
                        get("/api/v1/community/direct/conversations")
                                .param("filter", "requests")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").value(1))
                .andExpect(jsonPath("$.totalUnread").value(1));
        mvc.perform(
                        post(path + "/decision")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accept\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(
                        post(path + "/read")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sequence\":9223372036854775807}"))
                .andExpect(status().isNoContent());
        for (int i = 0; i < 2; i++)
            mvc.perform(
                            post(path + "/messages")
                                    .with(as(guest, "GUEST"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(send))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.body").value("Waiting"));
        mvc.perform(
                        get("/api/v1/community/direct/conversations")
                                .param("filter", "unread")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnread").value(1));
        mvc.perform(
                        post(path + "/messages")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(send.replace("Waiting", "Changed")))
                .andExpect(status().isConflict());
        mvc.perform(
                        post(path + "/messages")
                                .with(as(lecturer, "ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(send))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?", guest);
        mvc.perform(
                        post(path + "/messages")
                                .with(as(guest, "GUEST"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(send))
                .andExpect(status().isForbidden());
    }

    @Test
    void declineStopsChatRestartAndSelfMessagingIsRejected() throws Exception {
        String id =
                JsonPath.read(
                        directRequest(
                                guest,
                                "GUEST",
                                "lecture@community.test",
                                UUID.randomUUID(),
                                "Hello"),
                        "$.id");
        mvc.perform(
                        post("/api/v1/community/direct/conversations/" + id + "/decision")
                                .with(as(lecturer, "LECTURE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accept\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        for (String email :
                java.util.List.of(
                        "lecture@community.test",
                        "guest@community.test",
                        "missing@community.test")) {
            mvc.perform(
                            post("/api/v1/community/direct/conversations")
                                    .with(as(guest, "GUEST"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"email\":\""
                                                    + email
                                                    + "\",\"clientId\":\""
                                                    + UUID.randomUUID()
                                                    + "\",\"body\":\"Again\"}"))
                    .andExpect(
                            email.startsWith("lecture")
                                    ? status().isConflict()
                                    : email.startsWith("guest")
                                            ? status().isBadRequest()
                                            : status().isNotFound());
        }
    }

    @Test
    void directMessageHistoryUsesBoundedBidirectionalKeysetAndValidatesFilters() throws Exception {
        String id =
                JsonPath.read(
                        directRequest(
                                guest,
                                "GUEST",
                                "student@community.test",
                                UUID.randomUUID(),
                                "First"),
                        "$.id");
        for (int i = 0; i < 54; i++)
            jdbc.update(
                    "INSERT INTO"
                        + " community_direct_messages(id,conversation_id,author_id,client_id,body)"
                        + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    UUID.fromString(id),
                    guest,
                    UUID.randomUUID(),
                    "Message " + i);
        String path = "/api/v1/community/direct/conversations/" + id + "/messages";
        String result =
                mvc.perform(get(path).with(as(student, "STUDENT")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.messages.length()").value(50))
                        .andExpect(jsonPath("$.hasMore").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Number oldest = JsonPath.read(result, "$.oldestSequence"),
                newest = JsonPath.read(result, "$.newestSequence");
        mvc.perform(get(path).param("before", oldest.toString()).with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(5));
        mvc.perform(get(path).param("after", "0").with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(50))
                .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get(path).param("after", newest.toString()).with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get(path).param("after", "1").param("before", "2").with(as(student, "STUDENT")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/community/direct/conversations")
                                .param("filter", "unknown")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/community/direct/conversations")
                                .param("page", "-1")
                                .with(as(student, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reactionsReplaceOneUserChoiceAndPreserveLegacyLikeContract() throws Exception {
        String id = createPost(guest, "GUEST", "English practice", null);
        String path = "/api/v1/community/posts/" + id;
        for (String kind :
                java.util.List.of("LIKE", "LOVE", "CARE", "HAHA", "WOW", "SAD", "ANGRY")) {
            for (int retry = 0; retry < 2; retry++)
                mvc.perform(
                                post(path + "/reactions")
                                        .with(as(student, "STUDENT"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"kind\":\"" + kind + "\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.viewerReaction").value(kind))
                        .andExpect(jsonPath("$.likeCount").value(1))
                        .andExpect(jsonPath("$.reactionCounts." + kind).value(1));
        }
        mvc.perform(
                        post(path + "/reactions")
                                .with(as(student, "STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"kind\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/community/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].reactionCounts.ANGRY").value(1));
        mvc.perform(post(path + "/likes").with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerReaction").value("LIKE"));
        mvc.perform(delete(path + "/reactions").with(as(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByViewer").value(false));
        String space = createSpace(guest, "GUEST", "GROUP", "PRIVATE"),
                privateId = createPost(guest, "GUEST", "Private English", space);
        mvc.perform(
                        post("/api/v1/community/posts/" + privateId + "/reactions")
                                .with(as(student, "ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"kind\":\"LOVE\"}"))
                .andExpect(status().isForbidden());
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
