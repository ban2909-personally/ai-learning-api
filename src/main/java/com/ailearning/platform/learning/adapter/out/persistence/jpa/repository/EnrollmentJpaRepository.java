package com.ailearning.platform.learning.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.learning.adapter.out.persistence.jpa.entity.EnrollmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentJpaRepository extends JpaRepository<EnrollmentJpaEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO enrollments (id, user_id, course_id, status, enrolled_at)
            VALUES (:id, :userId, :courseId, 'ACTIVE', :enrolledAt)
            ON CONFLICT (user_id, course_id) DO NOTHING
            """, nativeQuery = true)
    int insertActiveIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("courseId") UUID courseId,
            @Param("enrolledAt") Instant enrolledAt
    );

    Optional<EnrollmentJpaEntity> findByUserIdAndCourseId(UUID userId, UUID courseId);

    List<EnrollmentJpaEntity> findAllByUserIdOrderByEnrolledAtDesc(UUID userId);
}
