package com.ailearning.platform.organization.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.organization.adapter.out.persistence.jpa.entity.OrganizationMembershipJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipJpaRepository
        extends JpaRepository<OrganizationMembershipJpaEntity, UUID> {
    Optional<OrganizationMembershipJpaEntity> findByOrganizationIdAndUserId(
            UUID organizationId,
            UUID userId
    );

    List<OrganizationMembershipJpaEntity> findByUserIdOrderByJoinedAtDescIdDesc(
            UUID userId,
            Pageable pageable
    );

    List<OrganizationMembershipJpaEntity> findByOrganizationIdOrderByJoinedAtAscIdAsc(
            UUID organizationId,
            Pageable pageable
    );
}
