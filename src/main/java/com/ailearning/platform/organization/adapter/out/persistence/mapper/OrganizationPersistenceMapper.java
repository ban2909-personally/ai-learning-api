package com.ailearning.platform.organization.adapter.out.persistence.mapper;

import com.ailearning.platform.organization.adapter.out.persistence.jpa.entity.OrganizationJpaEntity;
import com.ailearning.platform.organization.adapter.out.persistence.jpa.entity.OrganizationMembershipJpaEntity;
import com.ailearning.platform.organization.domain.model.Organization;
import com.ailearning.platform.organization.domain.model.OrganizationMembership;
import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;

public final class OrganizationPersistenceMapper {
    private OrganizationPersistenceMapper() {
    }

    public static Organization toDomain(OrganizationJpaEntity entity) {
        return new Organization(
                entity.getId(),
                new OrganizationSlug(entity.getSlug()),
                entity.getName(),
                entity.getCreatedBy(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt()
        );
    }

    public static OrganizationMembership toDomain(OrganizationMembershipJpaEntity entity) {
        return new OrganizationMembership(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getUserId(),
                entity.getRole(),
                entity.getJoinedAt()
        );
    }

    public static OrganizationMembershipJpaEntity toEntity(OrganizationMembership membership) {
        return new OrganizationMembershipJpaEntity(
                membership.id(),
                membership.organizationId(),
                membership.userId(),
                membership.role(),
                membership.joinedAt()
        );
    }
}
