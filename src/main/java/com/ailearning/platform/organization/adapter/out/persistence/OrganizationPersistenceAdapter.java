package com.ailearning.platform.organization.adapter.out.persistence;

import com.ailearning.platform.organization.adapter.out.persistence.jpa.entity.OrganizationJpaEntity;
import com.ailearning.platform.organization.adapter.out.persistence.jpa.entity.OrganizationMembershipJpaEntity;
import com.ailearning.platform.organization.adapter.out.persistence.jpa.repository.OrganizationJpaRepository;
import com.ailearning.platform.organization.adapter.out.persistence.jpa.repository.OrganizationMembershipJpaRepository;
import com.ailearning.platform.organization.adapter.out.persistence.mapper.OrganizationPersistenceMapper;
import com.ailearning.platform.organization.application.port.out.OrganizationCreation;
import com.ailearning.platform.organization.application.port.out.OrganizationMembershipDetails;
import com.ailearning.platform.organization.application.port.out.OrganizationStore;
import com.ailearning.platform.organization.domain.model.Organization;
import com.ailearning.platform.organization.domain.model.OrganizationMembership;
import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrganizationPersistenceAdapter implements OrganizationStore {
    private final OrganizationJpaRepository organizations;
    private final OrganizationMembershipJpaRepository memberships;

    public OrganizationPersistenceAdapter(
            OrganizationJpaRepository organizations,
            OrganizationMembershipJpaRepository memberships
    ) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMembershipDetails> findByCreationKey(
            UUID creatorId,
            UUID idempotencyKey
    ) {
        return organizations.findByCreatedByAndIdempotencyKey(creatorId, idempotencyKey)
                .map(this::ownerDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(OrganizationSlug slug) {
        return organizations.existsBySlug(slug.value());
    }

    @Override
    @Transactional
    public Optional<OrganizationCreation> insertWithOwnerOrGet(
            Organization organization,
            OrganizationMembership owner
    ) {
        int inserted = organizations.insertIfAbsent(
                organization.id(),
                organization.slug().value(),
                organization.name(),
                organization.createdBy(),
                organization.idempotencyKey(),
                organization.createdAt()
        );
        if (inserted == 1) {
            memberships.saveAndFlush(OrganizationPersistenceMapper.toEntity(owner));
            return Optional.of(new OrganizationCreation(
                    new OrganizationMembershipDetails(organization, owner),
                    true
            ));
        }
        return organizations.findByCreatedByAndIdempotencyKey(
                        organization.createdBy(),
                        organization.idempotencyKey()
                )
                .map(this::ownerDetails)
                .map(details -> new OrganizationCreation(details, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMembershipDetails> findByMember(UUID userId, int limit) {
        var memberRows = memberships.findByUserIdOrderByJoinedAtDescIdDesc(
                userId,
                PageRequest.of(0, limit)
        );
        if (memberRows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Organization> organizationsById = organizations.findAllById(
                        memberRows.stream()
                                .map(OrganizationMembershipJpaEntity::getOrganizationId)
                                .toList()
                ).stream()
                .map(OrganizationPersistenceMapper::toDomain)
                .collect(Collectors.toMap(Organization::id, Function.identity()));
        return memberRows.stream()
                .map(OrganizationPersistenceMapper::toDomain)
                .map(membership -> new OrganizationMembershipDetails(
                        requireOrganization(organizationsById, membership.organizationId()),
                        membership
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationMembership> findMembership(UUID organizationId, UUID userId) {
        return memberships.findByOrganizationIdAndUserId(organizationId, userId)
                .map(OrganizationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMembership> findMembers(UUID organizationId, int limit) {
        return memberships.findByOrganizationIdOrderByJoinedAtAscIdAsc(
                        organizationId,
                        PageRequest.of(0, limit)
                ).stream()
                .map(OrganizationPersistenceMapper::toDomain)
                .toList();
    }

    private OrganizationMembershipDetails ownerDetails(OrganizationJpaEntity organizationEntity) {
        OrganizationMembership owner = memberships.findByOrganizationIdAndUserId(
                        organizationEntity.getId(),
                        organizationEntity.getCreatedBy()
                )
                .map(OrganizationPersistenceMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Organization is missing its creator membership"));
        return new OrganizationMembershipDetails(
                OrganizationPersistenceMapper.toDomain(organizationEntity),
                owner
        );
    }

    private Organization requireOrganization(Map<UUID, Organization> organizationsById, UUID organizationId) {
        Organization organization = organizationsById.get(organizationId);
        if (organization == null) {
            throw new IllegalStateException("Membership references a missing organization");
        }
        return organization;
    }
}
