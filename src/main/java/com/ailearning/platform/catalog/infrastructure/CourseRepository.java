package com.ailearning.platform.catalog.infrastructure;

import com.ailearning.platform.catalog.domain.CourseEntity;
import com.ailearning.platform.catalog.domain.CourseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<CourseEntity, UUID>, JpaSpecificationExecutor<CourseEntity> {

    @EntityGraph(attributePaths = {"category", "instructor"})
    Optional<CourseEntity> findBySlugAndStatus(String slug, CourseStatus status);
}
