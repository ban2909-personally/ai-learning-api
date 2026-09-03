package com.ailearning.platform.mentoring.adapter.in.web.controller;

import com.ailearning.platform.mentoring.adapter.in.web.dto.request.AskMentorRequest;
import com.ailearning.platform.mentoring.adapter.in.web.dto.response.MentorAcceptedResponse;
import com.ailearning.platform.mentoring.adapter.in.web.dto.response.MentorCompletedResponse;
import com.ailearning.platform.mentoring.adapter.in.web.dto.response.MentorDeltaResponse;
import com.ailearning.platform.mentoring.adapter.in.web.dto.response.MentorMessageResponse;
import com.ailearning.platform.mentoring.adapter.in.web.dto.response.MentorStreamErrorResponse;
import com.ailearning.platform.mentoring.api.contract.MentorAnswerObserver;
import com.ailearning.platform.mentoring.api.contract.MentorMessageView;
import com.ailearning.platform.mentoring.api.contract.MentorTurnView;
import com.ailearning.platform.mentoring.api.usecase.MentorUseCase;
import com.ailearning.platform.mentoring.config.MentorWebProperties;
import com.ailearning.platform.mentoring.domain.valueobject.MentorQuestion;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/v1/me/courses/{courseSlug}/lessons/{lessonId}/mentor/messages")
public class MentorController {
    private final MentorUseCase mentor;
    private final TaskExecutor executor;
    private final MentorWebProperties properties;

    public MentorController(
            MentorUseCase mentor,
            @Qualifier("mentorTaskExecutor") TaskExecutor executor,
            MentorWebProperties properties
    ) {
        this.mentor = mentor;
        this.executor = executor;
        this.properties = properties;
    }

    @GetMapping
    List<MentorMessageResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseSlug,
            @PathVariable UUID lessonId
    ) {
        return mentor.history(userId(jwt), courseSlug, lessonId).stream()
                .map(MentorMessageResponse::from)
                .toList();
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter ask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseSlug,
            @PathVariable UUID lessonId,
            @Valid @RequestBody AskMentorRequest request
    ) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        UUID userId = userId(jwt);
        try {
            executor.execute(() -> streamAnswer(
                    emitter,
                    userId,
                    courseSlug,
                    lessonId,
                    MentorQuestion.from(request.question())
            ));
        } catch (RejectedExecutionException exception) {
            sendError(emitter, new MentorStreamErrorResponse(
                    "mentor_capacity_exceeded",
                    "AI Mentor đang xử lý quá nhiều yêu cầu. Vui lòng thử lại sau."
            ));
        }
        return emitter;
    }

    private void streamAnswer(
            SseEmitter emitter,
            UUID userId,
            String courseSlug,
            UUID lessonId,
            MentorQuestion question
    ) {
        try {
            mentor.ask(userId, courseSlug, lessonId, question, new SseMentorObserver(emitter));
        } catch (BusinessException exception) {
            sendError(emitter, new MentorStreamErrorResponse(exception.code(), exception.getMessage()));
        } catch (RuntimeException exception) {
            sendError(emitter, new MentorStreamErrorResponse(
                    "mentor_unavailable",
                    "AI Mentor đang tạm gián đoạn. Vui lòng thử lại sau."
            ));
        }
    }

    private void sendError(SseEmitter emitter, MentorStreamErrorResponse error) {
        try {
            emitter.send(SseEmitter.event().name("error").data(error));
        } catch (IOException | IllegalStateException ignored) {
            // The client has already disconnected; there is no response channel left to use.
        } finally {
            emitter.complete();
        }
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static final class SseMentorObserver implements MentorAnswerObserver {
        private final SseEmitter emitter;

        private SseMentorObserver(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void accepted(MentorMessageView userMessage, int remainingQuota) {
            send("message", new MentorAcceptedResponse(
                    MentorMessageResponse.from(userMessage), remainingQuota
            ));
        }

        @Override
        public void delta(String text) {
            send("delta", new MentorDeltaResponse(text));
        }

        @Override
        public void completed(MentorTurnView turn) {
            send("complete", new MentorCompletedResponse(
                    MentorMessageResponse.from(turn.assistantMessage()),
                    turn.remainingQuota()
            ));
            emitter.complete();
        }

        private void send(String eventName, Object data) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException exception) {
                throw new ClientDisconnectedException(exception);
            }
        }
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
