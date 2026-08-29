package com.ailearning.platform.learning.adapter.out.persistence;

import com.ailearning.platform.learning.adapter.out.persistence.jpa.repository.EnrollmentJpaRepository;
import com.ailearning.platform.learning.adapter.out.persistence.mapper.EnrollmentPersistenceMapper;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.model.Enrollment;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EnrollmentPersistenceAdapter implements EnrollmentStore {
    private final EnrollmentJpaRepository repository;
    public EnrollmentPersistenceAdapter(EnrollmentJpaRepository repository) { this.repository = repository; }
    @Override public void insertActiveIfAbsent(UUID id, UUID userId, UUID courseId, Instant enrolledAt) {
        repository.insertActiveIfAbsent(id, userId, courseId, enrolledAt);
    }
    @Override public Optional<Enrollment> find(UUID userId, UUID courseId) {
        return repository.findByUserIdAndCourseId(userId, courseId).map(EnrollmentPersistenceMapper::toDomain);
    }
    @Override public List<Enrollment> findByUser(UUID userId) {
        return repository.findAllByUserIdOrderByEnrolledAtDesc(userId).stream().map(EnrollmentPersistenceMapper::toDomain).toList();
    }
}
