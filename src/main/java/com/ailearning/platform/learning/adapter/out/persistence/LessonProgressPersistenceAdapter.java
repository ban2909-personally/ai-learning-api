package com.ailearning.platform.learning.adapter.out.persistence;

import com.ailearning.platform.learning.adapter.out.persistence.jpa.entity.LessonProgressJpaEntity;
import com.ailearning.platform.learning.adapter.out.persistence.jpa.repository.LessonProgressJpaRepository;
import com.ailearning.platform.learning.application.port.out.LessonProgressStore;
import com.ailearning.platform.learning.domain.model.LessonProgress;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class LessonProgressPersistenceAdapter implements LessonProgressStore {
    private final LessonProgressJpaRepository repository;
    public LessonProgressPersistenceAdapter(LessonProgressJpaRepository repository) { this.repository = repository; }
    @Override public Optional<LessonProgress> find(UUID enrollmentId, UUID lessonId) {
        return repository.findByEnrollmentIdAndLessonId(enrollmentId, lessonId).map(this::toDomain);
    }
    @Override public LessonProgress upsert(UUID id, UUID enrollmentId, UUID lessonId, int positionSeconds,
            boolean completed, Instant updatedAt) {
        repository.upsert(id, enrollmentId, lessonId, positionSeconds, completed, updatedAt);
        return find(enrollmentId, lessonId).orElseThrow();
    }
    private LessonProgress toDomain(LessonProgressJpaEntity entity) {
        return new LessonProgress(entity.getId(), entity.getEnrollmentId(), entity.getLessonId(),
                entity.getPositionSeconds(), entity.isCompleted(), entity.getUpdatedAt());
    }
}
