package com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "lessons")
public class LessonJpaEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSectionJpaEntity section;
    @Column(nullable = false, length = 180) private String title;
    @Column(name = "content_url", nullable = false, length = 1000) private String contentUrl;
    @Column(name = "duration_seconds", nullable = false) private int durationSeconds;
    @Column(nullable = false) private boolean preview;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    protected LessonJpaEntity() {}
    public CourseSectionJpaEntity getSection() { return section; }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getContentUrl() { return contentUrl; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isPreview() { return preview; }
    public int getDisplayOrder() { return displayOrder; }
}
