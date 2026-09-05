package com.ailearning.platform.commerce.adapter.in.web.controller;

import com.ailearning.platform.commerce.api.contract.CourseOrderView;
import com.ailearning.platform.commerce.api.contract.CreateCourseOrderResult;
import com.ailearning.platform.commerce.api.usecase.CourseOrderUseCase;
import com.ailearning.platform.commerce.application.command.CreateCourseOrderCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CourseOrderApiIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("8ec33d91-0cc4-445f-9266-5f44d7bca900");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("b6ccac9b-fe4e-48f3-8cd2-81dbbeb4ad01");
    private static final String ORIGIN = "http://localhost:5173";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_commerce_api_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseOrderUseCase orders;

    @Test
    void requiresAuthenticationForCreationAndHistory() throws Exception {
        mockMvc.perform(post("/api/v1/me/orders")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseSlug\":\"clean-architecture\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/orders"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test
    void createsFromJwtIdentityAndReturnsCreatedOnlyForTheFirstRequest() throws Exception {
        CourseOrderView order = orderView();
        when(orders.create(any(CreateCourseOrderCommand.class)))
                .thenReturn(new CreateCourseOrderResult(order, true))
                .thenReturn(new CreateCourseOrderResult(order, false));

        var request = post("/api/v1/me/orders")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseSlug\":\"clean-architecture\"}")
                .with(jwt().jwt(token -> token.subject(USER_ID.toString())));

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(order.id().toString()))
                .andExpect(jsonPath("$.courseSlug").value("clean-architecture"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.id().toString()));

        verify(orders, org.mockito.Mockito.times(2)).create(new CreateCourseOrderCommand(
                USER_ID,
                "clean-architecture",
                IDEMPOTENCY_KEY
        ));
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersBoundedHistory() throws Exception {
        when(orders.findRecent(USER_ID, 1)).thenReturn(List.of(orderView()));

        mockMvc.perform(get("/api/v1/me/orders")
                        .param("limit", "1")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseSlug").value("clean-architecture"));

        verify(orders).findRecent(USER_ID, 1);
    }

    @Test
    void rejectsMissingKeysInvalidBodiesAndUnboundedHistory() throws Exception {
        var authenticatedPost = post("/api/v1/me/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(USER_ID.toString())));

        mockMvc.perform(authenticatedPost.content("{\"courseSlug\":\"clean-architecture\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authenticatedPost
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content("{\"courseSlug\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(get("/api/v1/me/orders")
                        .param("limit", "101")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest());

        verify(orders, never()).create(any());
        verify(orders, never()).findRecent(any(), anyInt());
    }

    @Test
    void permitsTheIdempotencyHeaderInCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/me/orders")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Idempotency-Key"));
    }

    private CourseOrderView orderView() {
        return new CourseOrderView(
                UUID.fromString("d2199370-ff1d-43c4-94a5-31d9cb051b44"),
                UUID.fromString("58d19684-f4dc-46a7-b716-8ba176e185f3"),
                "clean-architecture",
                "Clean Architecture",
                new BigDecimal("499000.00"),
                "VND",
                "PENDING_PAYMENT",
                Instant.parse("2026-09-05T08:00:00Z"),
                Instant.parse("2026-09-05T08:30:00Z")
        );
    }
}
