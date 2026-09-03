package com.ailearning.platform.mentoring.application.service.impl;

import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContext;
import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContextLookup;
import com.ailearning.platform.mentoring.api.contract.MentorAnswerObserver;
import com.ailearning.platform.mentoring.api.contract.MentorMessageView;
import com.ailearning.platform.mentoring.api.contract.MentorTurnView;
import com.ailearning.platform.mentoring.application.port.out.MentorAiClient;
import com.ailearning.platform.mentoring.application.port.out.MentorConversationStore;
import com.ailearning.platform.mentoring.application.port.out.MentorQuota;
import com.ailearning.platform.mentoring.application.port.out.MentorTurnMonitor;
import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;
import com.ailearning.platform.mentoring.domain.model.MentorConversation;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;
import com.ailearning.platform.mentoring.domain.valueobject.MentorQuestion;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MentorServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID LESSON_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private final MentoringLessonContextLookup contexts = mock(MentoringLessonContextLookup.class);
    private final MentorConversationStore conversations = mock(MentorConversationStore.class);
    private final MentorQuota quota = mock(MentorQuota.class);
    private final MentorAiClient ai = mock(MentorAiClient.class);
    private final MentorTurnMonitor monitor = mock(MentorTurnMonitor.class);
    private final MentorAnswerObserver observer = mock(MentorAnswerObserver.class);
    private final MentorService service = new MentorService(
            contexts,
            conversations,
            quota,
            ai,
            monitor,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void persistsBothMessagesAndForwardsProviderDeltas() {
        var lesson = new MentoringLessonContext(COURSE_ID, "clean-spring", LESSON_ID, "Ports and adapters");
        var conversation = new MentorConversation(
                CONVERSATION_ID, USER_ID, COURSE_ID, LESSON_ID, NOW, NOW
        );
        List<MentorMessage> stored = new ArrayList<>();
        when(contexts.requireAccessible(USER_ID, "clean-spring", LESSON_ID)).thenReturn(lesson);
        when(quota.consume(USER_ID)).thenReturn(new MentorQuota.Decision(true, 19));
        when(conversations.findOrCreate(any(), eq(USER_ID), eq(COURSE_ID), eq(LESSON_ID), eq(NOW)))
                .thenReturn(conversation);
        when(conversations.append(any())).thenAnswer(invocation -> {
            MentorMessage message = invocation.getArgument(0);
            stored.add(message);
            return message;
        });
        when(conversations.findRecentMessages(CONVERSATION_ID, MentorService.PROVIDER_HISTORY_LIMIT))
                .thenAnswer(ignored -> List.copyOf(stored));
        when(ai.generate(eq(lesson), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<String> delta = invocation.getArgument(2);
            delta.accept("Use ");
            delta.accept("a port.");
            return new MentorAiClient.Result("Use a port.", "gpt-test", 24, 4);
        });

        MentorTurnView turn = service.ask(
                USER_ID,
                "clean-spring",
                LESSON_ID,
                MentorQuestion.from("How?"),
                observer
        );

        assertThat(stored).extracting(MentorMessage::role)
                .containsExactly(MentorMessageRole.USER, MentorMessageRole.ASSISTANT);
        assertThat(turn.assistantMessage().content()).isEqualTo("Use a port.");
        assertThat(turn.remainingQuota()).isEqualTo(19);
        var order = inOrder(observer);
        order.verify(observer).accepted(any(MentorMessageView.class), eq(19));
        order.verify(observer).delta("Use ");
        order.verify(observer).delta("a port.");
        order.verify(observer).completed(turn);
        verify(monitor).accepted();
        verify(monitor).completed();
    }

    @Test
    void rejectsAQuestionBeforePersistenceWhenQuotaIsExhausted() {
        when(contexts.requireAccessible(USER_ID, "clean-spring", LESSON_ID)).thenReturn(
                new MentoringLessonContext(COURSE_ID, "clean-spring", LESSON_ID, "Ports")
        );
        when(quota.consume(USER_ID)).thenReturn(new MentorQuota.Decision(false, 0));

        assertThatThrownBy(() -> service.ask(
                USER_ID,
                "clean-spring",
                LESSON_ID,
                MentorQuestion.from("How?"),
                observer
        )).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("mentor_quota_exceeded");

        verify(conversations, never()).append(any());
        verify(ai, never()).generate(any(), any(), any());
        verify(monitor).rejected("quota");
    }

    @Test
    void checksLessonAccessBeforeReturningHistory() {
        when(contexts.requireAccessible(USER_ID, "clean-spring", LESSON_ID)).thenReturn(
                new MentoringLessonContext(COURSE_ID, "clean-spring", LESSON_ID, "Ports")
        );
        when(conversations.find(USER_ID, LESSON_ID)).thenReturn(Optional.empty());

        assertThat(service.history(USER_ID, "clean-spring", LESSON_ID)).isEmpty();

        var order = inOrder(contexts, conversations);
        order.verify(contexts).requireAccessible(USER_ID, "clean-spring", LESSON_ID);
        order.verify(conversations).find(USER_ID, LESSON_ID);
    }

    @Test
    void doesNotPersistAnInvalidProviderAnswer() {
        var lesson = new MentoringLessonContext(COURSE_ID, "clean-spring", LESSON_ID, "Ports");
        when(contexts.requireAccessible(USER_ID, "clean-spring", LESSON_ID)).thenReturn(lesson);
        when(quota.consume(USER_ID)).thenReturn(new MentorQuota.Decision(true, 18));
        when(conversations.findOrCreate(any(), any(), any(), any(), any())).thenReturn(
                new MentorConversation(CONVERSATION_ID, USER_ID, COURSE_ID, LESSON_ID, NOW, NOW)
        );
        when(conversations.append(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversations.findRecentMessages(any(), anyInt())).thenReturn(List.of());
        when(ai.generate(any(), any(), any())).thenReturn(new MentorAiClient.Result(" ", "gpt-test", 1, 0));

        assertThatThrownBy(() -> service.ask(
                USER_ID, "clean-spring", LESSON_ID, MentorQuestion.from("How?"), observer
        )).isInstanceOf(BusinessException.class);

        ArgumentCaptor<MentorMessage> messages = ArgumentCaptor.forClass(MentorMessage.class);
        verify(conversations).append(messages.capture());
        assertThat(messages.getValue().role()).isEqualTo(MentorMessageRole.USER);
        verify(monitor).failed("mentor_invalid_response");
    }
}
