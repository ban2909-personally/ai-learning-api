package com.ailearning.platform.organization.adapter.out.persistence.jpa.entity;

import com.ailearning.platform.organization.domain.enums.OrganizationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_memberships")
public class OrganizationMembershipJpaEntity {
    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected OrganizationMembershipJpaEntity() {
    }

    public OrganizationMembershipJpaEntity(
            UUID id,
            UUID organizationId,
            UUID userId,
            OrganizationRole role,
            Instant joinedAt
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
