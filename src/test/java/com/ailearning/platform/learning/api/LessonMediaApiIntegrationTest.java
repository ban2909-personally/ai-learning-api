package com.ailearning.platform.learning.api;

import com.ailearning.platform.catalog.application.port.out.LessonMediaStorage;
import com.ailearning.platform.catalog.domain.model.LessonMediaAsset;
import com.ailearning.platform.platform.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/lesson-media-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/lesson-media-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LessonMediaApiIntegrationTest {
    private static final UUID OWNER_ID = UUID.fromString("df353774-10f6-4c7a-965b-8573113d37e8");
    private static final UUID STUDENT_ID = UUID.fromString("27fdd7d8-3972-45b4-82cb-4056b59ec461");
    private static final UUID ADMIN_ID = UUID.fromString("1c17a33e-8734-4d98-b139-a2f797f2fe79");
    private static final UUID LESSON_ID = UUID.fromString("7c13978f-790b-4df4-9164-20c0af74c45b");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_media_test")
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

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    SecurityProperties securityProperties;

    @MockitoBean
    LessonMediaStorage storage;

    @Test
    void ownerInstructorUploadsLessonMedia() throws Exception {
        byte[] content = {1, 2, 3, 4};
        var file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", content);
        when(storage.store(anyString(), anyString(), anyLong(), any())).thenAnswer(invocation ->
                new LessonMediaAsset(invocation.getArgument(0), "video/mp4", content.length, "new-etag"));

        mockMvc.perform(multipart("/api/v1/instructor/courses/media-delivery-test/lessons/{lessonId}/media", LESSON_ID)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(token -> token
                                        .subject(OWNER_ID.toString())
                                        .claim("roles", List.of("INSTRUCTOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.contentUrl").value(
                        "/api/v1/media/courses/media-delivery-test/lessons/" + LESSON_ID
                ));
    }

    @Test
    void studentCannotUploadLessonMedia() throws Exception {
        var file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/instructor/courses/media-delivery-test/lessons/{lessonId}/media", LESSON_ID)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(token -> token
                                        .subject(STUDENT_ID.toString())
                                        .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanUploadMediaForAnotherInstructorsCourse() throws Exception {
        byte[] content = {1, 2, 3, 4};
        var file = new MockMultipartFile("file", "lesson.webm", "video/webm", content);
        when(storage.store(anyString(), anyString(), anyLong(), any())).thenAnswer(invocation ->
                new LessonMediaAsset(invocation.getArgument(0), "video/webm", content.length, "admin-etag"));

        mockMvc.perform(multipart("/api/v1/instructor/courses/media-delivery-test/lessons/{lessonId}/media", LESSON_ID)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(token -> token
                                        .subject(ADMIN_ID.toString())
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("video/webm"));
    }

    @Test
    void enrolledStudentStreamsRequestedRangeWithMediaCookie() throws Exception {
        when(storage.open("courses/course/lessons/lesson/existing", 2, 4))
                .thenReturn(new ByteArrayInputStream("2345".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        MvcResult result = mockMvc.perform(get(
                                "/api/v1/media/courses/media-delivery-test/lessons/{lessonId}",
                                LESSON_ID
                        )
                        .header(HttpHeaders.RANGE, "bytes=2-5")
                        .cookie(new Cookie("media_access", mediaAccessToken())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4))
                .andExpect(content().bytes("2345".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void anonymousUserCannotStreamLessonMedia() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/media/courses/media-delivery-test/lessons/{lessonId}",
                        LESSON_ID
                ))
                .andExpect(status().isUnauthorized());
    }

    private String mediaAccessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(STUDENT_ID.toString())
                .claim("roles", List.of("STUDENT"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
