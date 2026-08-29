package com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CourseSectionJpaEntity;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CourseSectionJpaRepository extends JpaRepository<CourseSectionJpaEntity, UUID> {
    @EntityGraph(attributePaths = {"course", "lessons"})
    List<CourseSectionJpaEntity> findAllByCourseSlugAndCourseStatusOrderByDisplayOrderAsc(String slug, CourseStatus status);
}
