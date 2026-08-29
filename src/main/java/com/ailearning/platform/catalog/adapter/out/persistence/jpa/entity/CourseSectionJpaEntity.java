package com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "course_sections")
public class CourseSectionJpaEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private CourseJpaEntity course;
    @Column(nullable = false, length = 180) private String title;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @OneToMany(mappedBy = "section", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<LessonJpaEntity> lessons = new ArrayList<>();
    protected CourseSectionJpaEntity() {}
    public UUID getId() { return id; }
    public CourseJpaEntity getCourse() { return course; }
    public String getTitle() { return title; }
    public int getDisplayOrder() { return displayOrder; }
    public List<LessonJpaEntity> getLessons() { return lessons; }
}
