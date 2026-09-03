package com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.LessonJpaEntity;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LessonJpaRepository extends JpaRepository<LessonJpaEntity, UUID> {
    @EntityGraph(attributePaths = {"section", "section.course"})
    Optional<LessonJpaEntity> findByIdAndSectionCourseSlugAndSectionCourseStatus(
            UUID id, String courseSlug, CourseStatus status);

    @EntityGraph(attributePaths = {"section", "section.course", "section.course.instructor"})
    Optional<LessonJpaEntity> findByIdAndSectionCourseSlug(UUID id, String courseSlug);
}
