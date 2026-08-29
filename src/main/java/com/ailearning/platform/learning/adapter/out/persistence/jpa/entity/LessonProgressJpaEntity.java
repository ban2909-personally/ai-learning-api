package com.ailearning.platform.learning.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_progress")
public class LessonProgressJpaEntity {
    @Id private UUID id;
    @Column(name = "enrollment_id", nullable = false) private UUID enrollmentId;
    @Column(name = "lesson_id", nullable = false) private UUID lessonId;
    @Column(name = "position_seconds", nullable = false) private int positionSeconds;
    @Column(nullable = false) private boolean completed;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected LessonProgressJpaEntity() {}
    public UUID getId() { return id; }
    public UUID getEnrollmentId() { return enrollmentId; }
    public UUID getLessonId() { return lessonId; }
    public int getPositionSeconds() { return positionSeconds; }
    public boolean isCompleted() { return completed; }
    public Instant getUpdatedAt() { return updatedAt; }
}
