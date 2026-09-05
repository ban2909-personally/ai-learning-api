package com.ailearning.platform.commerce.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_orders")
public class CourseOrderJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_slug", nullable = false, length = 160)
    private String courseSlug;

    @Column(name = "course_title", nullable = false, length = 180)
    private String courseTitle;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CourseOrderJpaEntity() {
    }

    public CourseOrderJpaEntity(
            UUID id,
            UUID userId,
            UUID courseId,
            String courseSlug,
            String courseTitle,
            BigDecimal amount,
            String currency,
            UUID idempotencyKey,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.courseId = courseId;
        this.courseSlug = courseSlug;
        this.courseTitle = courseTitle;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getCourseSlug() {
        return courseSlug;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
