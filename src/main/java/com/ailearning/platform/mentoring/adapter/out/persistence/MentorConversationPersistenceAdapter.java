package com.ailearning.platform.mentoring.adapter.out.persistence;

import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.entity.MentorConversationJpaEntity;
import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.entity.MentorMessageJpaEntity;
import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.repository.MentorConversationJpaRepository;
import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.repository.MentorMessageJpaRepository;
import com.ailearning.platform.mentoring.application.port.out.MentorConversationStore;
import com.ailearning.platform.mentoring.domain.model.MentorConversation;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class MentorConversationPersistenceAdapter implements MentorConversationStore {
    private final MentorConversationJpaRepository conversations;
    private final MentorMessageJpaRepository messages;

    public MentorConversationPersistenceAdapter(
            MentorConversationJpaRepository conversations,
            MentorMessageJpaRepository messages
    ) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MentorConversation> find(UUID userId, UUID lessonId) {
        return conversations.findByUserIdAndLessonId(userId, lessonId).map(this::toDomain);
    }

    @Override
    @Transactional
    public MentorConversation findOrCreate(
            UUID conversationId,
            UUID userId,
            UUID courseId,
            UUID lessonId,
            Instant now
    ) {
        conversations.upsert(conversationId, userId, courseId, lessonId, now);
        return conversations.findByUserIdAndLessonId(userId, lessonId)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalStateException("Mentor conversation upsert did not return a row"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorMessage> findRecentMessages(UUID conversationId, int limit) {
        List<MentorMessage> recent = messages.findByConversationIdOrderByCreatedAtDescIdDesc(
                        conversationId,
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toDomain)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        java.util.Collections.reverse(recent);
        return List.copyOf(recent);
    }

    @Override
    @Transactional
    public MentorMessage append(MentorMessage message) {
        MentorMessageJpaEntity saved = messages.save(new MentorMessageJpaEntity(
                message.id(),
                message.conversationId(),
                message.role(),
                message.content(),
                message.providerModel(),
                message.inputTokens(),
                message.outputTokens(),
                message.createdAt()
        ));
        return toDomain(saved);
    }

    private MentorConversation toDomain(MentorConversationJpaEntity entity) {
        return new MentorConversation(
                entity.getId(),
                entity.getUserId(),
                entity.getCourseId(),
                entity.getLessonId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MentorMessage toDomain(MentorMessageJpaEntity entity) {
        return new MentorMessage(
                entity.getId(),
                entity.getConversationId(),
                entity.getRole(),
                entity.getContent(),
                entity.getProviderModel(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getCreatedAt()
        );
    }
}

