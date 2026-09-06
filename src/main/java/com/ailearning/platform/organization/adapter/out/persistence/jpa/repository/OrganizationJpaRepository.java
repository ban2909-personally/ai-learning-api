package com.ailearning.platform.organization.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.organization.adapter.out.persistence.jpa.entity.OrganizationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationJpaEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO organizations (id, slug, name, created_by, idempotency_key, created_at)
            VALUES (:id, :slug, :name, :createdBy, :idempotencyKey, :createdAt)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("slug") String slug,
            @Param("name") String name,
            @Param("createdBy") UUID createdBy,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("createdAt") Instant createdAt
    );

    Optional<OrganizationJpaEntity> findByCreatedByAndIdempotencyKey(
            UUID createdBy,
            UUID idempotencyKey
    );

    boolean existsBySlug(String slug);
}
