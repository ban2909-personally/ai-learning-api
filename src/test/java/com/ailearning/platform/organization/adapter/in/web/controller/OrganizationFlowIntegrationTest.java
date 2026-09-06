package com.ailearning.platform.organization.adapter.in.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/organization-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/organization-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrganizationFlowIntegrationTest {
    private static final String OWNER_ID = "aa57ecf4-bcb4-4ca4-91f9-a23c2f9aee11";
    private static final String OUTSIDER_ID = "ca6a0d72-eaec-4dd2-b375-7bbc75b09934";
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("213a01fb-2ad7-49d2-ae23-d331f5e5ea26");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_organization_flow_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsReplaysListsAndProtectsRosterThroughRealAdapters() throws Exception {
        var create = post("/api/v1/me/organizations")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme Learning\",\"slug\":\"acme-learning\"}")
                .with(jwt().jwt(token -> token.subject(OWNER_ID)));

        String response = mockMvc.perform(create)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acme-learning"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        String organizationId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(create)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(organizationId));
        mockMvc.perform(get("/api/v1/me/organizations")
                        .with(jwt().jwt(token -> token.subject(OWNER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(organizationId));
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/members", organizationId)
                        .with(jwt().jwt(token -> token.subject(OWNER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(OWNER_ID));
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/members", organizationId)
                        .with(jwt().jwt(token -> token.subject(OUTSIDER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("organization_not_found"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE created_by = ?",
                Integer.class,
                UUID.fromString(OWNER_ID)
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_memberships WHERE organization_id = ?",
                Integer.class,
                UUID.fromString(organizationId)
        )).isOne();
    }
}
