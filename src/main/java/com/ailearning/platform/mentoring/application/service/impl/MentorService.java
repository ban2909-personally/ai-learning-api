package com.ailearning.platform.mentoring.application.service.impl;

import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContextLookup;
import com.ailearning.platform.mentoring.api.contract.MentorAnswerObserver;
import com.ailearning.platform.mentoring.api.contract.MentorMessageView;
import com.ailearning.platform.mentoring.api.contract.MentorTurnView;
import com.ailearning.platform.mentoring.api.usecase.MentorUseCase;
import com.ailearning.platform.mentoring.application.port.out.MentorAiClient;
import com.ailearning.platform.mentoring.application.port.out.MentorConversationStore;
import com.ailearning.platform.mentoring.application.port.out.MentorQuota;
import com.ailearning.platform.mentoring.application.port.out.MentorTurnMonitor;
import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;
import com.ailearning.platform.mentoring.domain.valueobject.MentorQuestion;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class MentorService implements MentorUseCase {
    static final int PROVIDER_HISTORY_LIMIT = 12;
    static final int HISTORY_RESPONSE_LIMIT = 50;
    static final int MAX_PERSISTED_ANSWER_LENGTH = 12000;

    private final MentoringLessonContextLookup lessonContexts;
    private final MentorConversationStore conversations;
    private final MentorQuota quota;
    private final MentorAiClient aiClient;
    private final MentorTurnMonitor monitor;
    private final Clock clock;

    public MentorService(
            MentoringLessonContextLookup lessonContexts,
            MentorConversationStore conversations,
            MentorQuota quota,
            MentorAiClient aiClient,
            MentorTurnMonitor monitor,
            Clock clock
    ) {
        this.lessonContexts = lessonContexts;
        this.conversations = conversations;
        this.quota = quota;
        this.aiClient = aiClient;
        this.monitor = monitor;
        this.clock = clock;
    }

    @Override
    public List<MentorMessageView> history(UUID userId, String courseSlug, UUID lessonId) {
        lessonContexts.requireAccessible(userId, courseSlug, lessonId);
        return conversations.find(userId, lessonId)
                .map(conversation -> conversations.findRecentMessages(
                        conversation.id(), HISTORY_RESPONSE_LIMIT
                ))
                .orElseGet(List::of)
                .stream()
                .map(MentorService::view)
                .toList();
    }

    @Override
    public MentorTurnView ask(
            UUID userId,
            String courseSlug,
            UUID lessonId,
            MentorQuestion question,
            MentorAnswerObserver observer
    ) {
        var lesson = lessonContexts.requireAccessible(userId, courseSlug, lessonId);
        MentorQuota.Decision decision = quota.consume(userId);
        if (!decision.allowed()) {
            monitor.rejected("quota");
            throw new BusinessException(
                    "mentor_quota_exceeded",
                    ErrorType.TOO_MANY_REQUESTS,
                    "Bạn đã dùng hết lượt hỏi AI Mentor trong khung thời gian hiện tại."
            );
        }

        var now = clock.instant();
        var conversation = conversations.findOrCreate(
                UUID.randomUUID(), userId, lesson.courseId(), lesson.lessonId(), now
        );
        MentorMessage userMessage = conversations.append(new MentorMessage(
                UUID.randomUUID(),
                conversation.id(),
                MentorMessageRole.USER,
                question.value(),
                null,
                null,
                null,
                now
        ));
        monitor.accepted();
        observer.accepted(view(userMessage), decision.remaining());

        try {
            List<MentorMessage> promptHistory = conversations.findRecentMessages(
                    conversation.id(), PROVIDER_HISTORY_LIMIT
            );
            MentorAiClient.Result generated = aiClient.generate(lesson, promptHistory, observer::delta);
            if (generated.content() == null
                    || generated.content().isBlank()
                    || generated.content().length() > MAX_PERSISTED_ANSWER_LENGTH) {
                throw new BusinessException(
                        "mentor_invalid_response",
                        ErrorType.SERVICE_UNAVAILABLE,
                        "AI Mentor chưa thể tạo câu trả lời hợp lệ."
                );
            }
            MentorMessage assistantMessage = conversations.append(new MentorMessage(
                    UUID.randomUUID(),
                    conversation.id(),
                    MentorMessageRole.ASSISTANT,
                    generated.content(),
                    generated.model(),
                    generated.inputTokens(),
                    generated.outputTokens(),
                    clock.instant()
            ));
            MentorTurnView turn = new MentorTurnView(
                    view(userMessage), view(assistantMessage), decision.remaining()
            );
            monitor.completed();
            observer.completed(turn);
            return turn;
        } catch (RuntimeException exception) {
            monitor.failed(exception instanceof BusinessException business ? business.code() : "unexpected");
            throw exception;
        }
    }

    private static MentorMessageView view(MentorMessage message) {
        return new MentorMessageView(message.id(), message.role(), message.content(), message.createdAt());
    }
}
