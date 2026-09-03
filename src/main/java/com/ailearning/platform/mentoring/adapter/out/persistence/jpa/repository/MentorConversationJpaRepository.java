package com.ailearning.platform.mentoring.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.entity.MentorConversationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MentorConversationJpaRepository extends JpaRepository<MentorConversationJpaEntity, UUID> {
    Optional<MentorConversationJpaEntity> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    @Modifying
    @Query(value = """
            INSERT INTO mentor_conversations (
                id, user_id, course_id, lesson_id, created_at, updated_at
            ) VALUES (
                :id, :userId, :courseId, :lessonId, :now, :now
            )
            ON CONFLICT (user_id, lesson_id) DO UPDATE
                SET updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId,
            @Param("now") Instant now
    );
}

