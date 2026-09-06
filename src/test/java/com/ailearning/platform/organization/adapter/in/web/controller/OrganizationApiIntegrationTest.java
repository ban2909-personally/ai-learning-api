package com.ailearning.platform.organization.adapter.in.web.controller;

import com.ailearning.platform.organization.api.contract.CreateOrganizationResult;
import com.ailearning.platform.organization.api.contract.OrganizationMemberView;
import com.ailearning.platform.organization.api.contract.OrganizationView;
import com.ailearning.platform.organization.api.usecase.OrganizationUseCase;
import com.ailearning.platform.organization.application.command.CreateOrganizationCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrganizationApiIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("aa57ecf4-bcb4-4ca4-91f9-a23c2f9aee11");
    private static final UUID ORGANIZATION_ID = UUID.fromString("38aef04d-b228-4bf4-8e87-dffea95fd312");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("213a01fb-2ad7-49d2-ae23-d331f5e5ea26");
    private static final Instant CREATED_AT = Instant.parse("2026-09-06T02:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_organization_api_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean OrganizationUseCase organizations;

    @Test
    void requiresAuthenticationForEveryEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/me/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/organizations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/members", ORGANIZATION_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(organizations);
    }

    @Test
    void createsFromJwtIdentityAndDistinguishesReplay() throws Exception {
        OrganizationView organization = organizationView();
        when(organizations.create(any(CreateOrganizationCommand.class)))
                .thenReturn(new CreateOrganizationResult(organization, true))
                .thenReturn(new CreateOrganizationResult(organization, false));
        var request = post("/api/v1/me/organizations")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody())
                .with(jwt().jwt(token -> token.subject(USER_ID.toString())));

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.slug").value("acme-learning"))
                .andExpect(jsonPath("$.role").value("OWNER"));
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORGANIZATION_ID.toString()));

        verify(organizations, org.mockito.Mockito.times(2)).create(new CreateOrganizationCommand(
                USER_ID,
                "Acme Learning",
                "acme-learning",
                IDEMPOTENCY_KEY
        ));
    }

    @Test
    void listsOnlyTheJwtUsersOrganizationsAndAuthorizedRoster() throws Exception {
        UUID memberId = UUID.fromString("ca6a0d72-eaec-4dd2-b375-7bbc75b09934");
        when(organizations.findMine(USER_ID, 1)).thenReturn(List.of(organizationView()));
        when(organizations.findMembers(USER_ID, ORGANIZATION_ID, 1))
                .thenReturn(List.of(new OrganizationMemberView(memberId, "MEMBER", CREATED_AT)));

        mockMvc.perform(get("/api/v1/me/organizations")
                        .param("limit", "1")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("acme-learning"));
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/members", ORGANIZATION_ID)
                        .param("limit", "1")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(memberId.toString()))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));

        verify(organizations).findMine(USER_ID, 1);
        verify(organizations).findMembers(USER_ID, ORGANIZATION_ID, 1);
    }

    @Test
    void rejectsMalformedCreationAndUnboundedQueriesBeforeCallingTheUseCase() throws Exception {
        var post = post("/api/v1/me/organizations")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(USER_ID.toString())));

        mockMvc.perform(post.content("{\"name\":\"A\",\"slug\":\"Not Safe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(get("/api/v1/me/organizations")
                        .param("limit", "101")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/members", ORGANIZATION_ID)
                        .param("limit", "0")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest());

        verify(organizations, never()).create(any());
        verify(organizations, never()).findMine(any(), anyInt());
        verify(organizations, never()).findMembers(any(), any(), anyInt());
    }

    private String validBody() {
        return "{\"name\":\"Acme Learning\",\"slug\":\"acme-learning\"}";
    }

    private OrganizationView organizationView() {
        return new OrganizationView(
                ORGANIZATION_ID,
                "acme-learning",
                "Acme Learning",
                "OWNER",
                CREATED_AT,
                CREATED_AT
        );
    }
}
