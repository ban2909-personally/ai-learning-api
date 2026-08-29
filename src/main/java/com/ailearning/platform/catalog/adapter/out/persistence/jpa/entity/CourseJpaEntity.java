package com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity;

import com.ailearning.platform.catalog.domain.enums.CourseLevel;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class CourseJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorJpaEntity instructor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryJpaEntity category;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "short_description", nullable = false, length = 320)
    private String shortDescription;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourseLevel level;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourseStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseJpaEntity() {
    }

    public UUID getId() { return id; }
    public InstructorJpaEntity getInstructor() { return instructor; }
    public CategoryJpaEntity getCategory() { return category; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public CourseLevel getLevel() { return level; }
    public String getLanguage() { return language; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public CourseStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
}
