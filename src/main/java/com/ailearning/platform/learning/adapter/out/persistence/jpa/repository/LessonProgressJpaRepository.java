package com.ailearning.platform.learning.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.learning.adapter.out.persistence.jpa.entity.LessonProgressJpaEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressJpaEntity, UUID> {
    Optional<LessonProgressJpaEntity> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);
    @Modifying
    @Query(value = """
            INSERT INTO lesson_progress (id, enrollment_id, lesson_id, position_seconds, completed, updated_at)
            VALUES (:id, :enrollmentId, :lessonId, :positionSeconds, :completed, :updatedAt)
            ON CONFLICT (enrollment_id, lesson_id) DO UPDATE SET
                position_seconds = EXCLUDED.position_seconds,
                completed = lesson_progress.completed OR EXCLUDED.completed,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("enrollmentId") UUID enrollmentId, @Param("lessonId") UUID lessonId,
            @Param("positionSeconds") int positionSeconds, @Param("completed") boolean completed,
            @Param("updatedAt") Instant updatedAt);
}
