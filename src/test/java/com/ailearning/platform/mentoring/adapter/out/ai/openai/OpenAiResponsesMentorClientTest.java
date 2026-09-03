package com.ailearning.platform.mentoring.adapter.out.ai.openai;

import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContext;
import com.ailearning.platform.mentoring.config.OpenAiMentorProperties;
import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiResponsesMentorClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsAStatelessResponsesRequestAndParsesTextDeltas() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> requestPayload = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPayload.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = """
                    data: {"type":"response.output_text.delta","delta":"Start "}

                    data: {"type":"response.output_text.delta","delta":"small."}

                    data: {"type":"response.completed","response":{"model":"gpt-test-2026","usage":{"input_tokens":21,"output_tokens":3}}}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        OpenAiResponsesMentorClient client = client("test-secret");
        List<String> deltas = new ArrayList<>();

        var result = client.generate(
                new MentoringLessonContext(
                        UUID.randomUUID(), "clean-spring", UUID.randomUUID(), "Dependency inversion"
                ),
                List.of(new MentorMessage(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        MentorMessageRole.USER,
                        "How do I begin?",
                        null,
                        null,
                        null,
                        Instant.now()
                )),
                deltas::add
        );

        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
        assertThat(requestPayload.get().path("store").asBoolean()).isFalse();
        assertThat(requestPayload.get().path("stream").asBoolean()).isTrue();
        assertThat(requestPayload.get().path("input").toString())
                .contains("Dependency inversion", "How do I begin?");
        assertThat(deltas).containsExactly("Start ", "small.");
        assertThat(result.content()).isEqualTo("Start small.");
        assertThat(result.model()).isEqualTo("gpt-test-2026");
        assertThat(result.inputTokens()).isEqualTo(21);
        assertThat(result.outputTokens()).isEqualTo(3);
    }

    @Test
    void failsSafelyBeforeNetworkWhenApiKeyIsMissing() {
        OpenAiResponsesMentorClient client = client("");

        assertThatThrownBy(() -> client.generate(
                new MentoringLessonContext(
                        UUID.randomUUID(), "course", UUID.randomUUID(), "Lesson"
                ),
                List.of(),
                ignored -> { }
        )).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("mentor_provider_unavailable");
    }

    private OpenAiResponsesMentorClient client(String apiKey) {
        URI baseUrl = server == null
                ? URI.create("http://127.0.0.1:1/v1/")
                : URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        return new OpenAiResponsesMentorClient(objectMapper, new OpenAiMentorProperties(
                baseUrl,
                apiKey,
                "gpt-test",
                200,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)
        ));
    }
}
