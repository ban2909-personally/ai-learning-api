package com.ailearning.platform.mentoring.application.port.out;

import com.ailearning.platform.mentoring.domain.model.MentorConversation;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MentorConversationStore {
    Optional<MentorConversation> find(UUID userId, UUID lessonId);

    MentorConversation findOrCreate(
            UUID conversationId,
            UUID userId,
            UUID courseId,
            UUID lessonId,
            Instant now
    );

    List<MentorMessage> findRecentMessages(UUID conversationId, int limit);

    MentorMessage append(MentorMessage message);
}
