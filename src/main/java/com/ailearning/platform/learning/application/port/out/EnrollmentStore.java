package com.ailearning.platform.learning.application.port.out;

import com.ailearning.platform.learning.domain.model.Enrollment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentStore {
    void insertActiveIfAbsent(UUID id, UUID userId, UUID courseId, Instant enrolledAt);
    Optional<Enrollment> find(UUID userId, UUID courseId);
    List<Enrollment> findByUser(UUID userId);
}
