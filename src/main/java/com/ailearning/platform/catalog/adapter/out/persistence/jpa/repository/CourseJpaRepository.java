package com.ailearning.platform.catalog.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CourseJpaEntity;
import com.ailearning.platform.catalog.domain.enums.CourseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CourseJpaRepository extends JpaRepository<CourseJpaEntity, UUID>, JpaSpecificationExecutor<CourseJpaEntity> {

    @EntityGraph(attributePaths = {"category", "instructor"})
    Optional<CourseJpaEntity> findBySlugAndStatus(String slug, CourseStatus status);
    @EntityGraph(attributePaths = {"category", "instructor"})
    Optional<CourseJpaEntity> findByIdAndStatus(UUID id, CourseStatus status);
}
