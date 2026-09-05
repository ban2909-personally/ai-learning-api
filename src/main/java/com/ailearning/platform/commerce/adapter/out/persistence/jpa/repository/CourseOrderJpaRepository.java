package com.ailearning.platform.commerce.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.commerce.adapter.out.persistence.jpa.entity.CourseOrderJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseOrderJpaRepository extends JpaRepository<CourseOrderJpaEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO course_orders (
                id, user_id, course_id, course_slug, course_title, amount, currency,
                idempotency_key, created_at, expires_at
            ) VALUES (
                :id, :userId, :courseId, :courseSlug, :courseTitle, :amount, :currency,
                :idempotencyKey, :createdAt, :expiresAt
            )
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("courseId") UUID courseId,
            @Param("courseSlug") String courseSlug,
            @Param("courseTitle") String courseTitle,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );

    Optional<CourseOrderJpaEntity> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);

    List<CourseOrderJpaEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);
}
