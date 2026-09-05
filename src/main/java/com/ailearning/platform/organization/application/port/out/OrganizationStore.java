package com.ailearning.platform.organization.application.port.out;

import com.ailearning.platform.organization.domain.model.Organization;
import com.ailearning.platform.organization.domain.model.OrganizationMembership;
import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationStore {
    Optional<OrganizationMembershipDetails> findByCreationKey(UUID creatorId, UUID idempotencyKey);

    boolean existsBySlug(OrganizationSlug slug);

    Optional<OrganizationCreation> insertWithOwnerOrGet(
            Organization organization,
            OrganizationMembership owner
    );

    List<OrganizationMembershipDetails> findByMember(UUID userId, int limit);

    Optional<OrganizationMembership> findMembership(UUID organizationId, UUID userId);

    List<OrganizationMembership> findMembers(UUID organizationId, int limit);
}
