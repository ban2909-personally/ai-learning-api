package com.ailearning.platform.organization.application.service.impl;

import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.organization.api.contract.CreateOrganizationResult;
import com.ailearning.platform.organization.api.contract.OrganizationMemberView;
import com.ailearning.platform.organization.api.contract.OrganizationView;
import com.ailearning.platform.organization.api.usecase.OrganizationUseCase;
import com.ailearning.platform.organization.application.command.CreateOrganizationCommand;
import com.ailearning.platform.organization.application.port.out.OrganizationCreation;
import com.ailearning.platform.organization.application.port.out.OrganizationMembershipDetails;
import com.ailearning.platform.organization.application.port.out.OrganizationStore;
import com.ailearning.platform.organization.domain.enums.OrganizationRole;
import com.ailearning.platform.organization.domain.model.Organization;
import com.ailearning.platform.organization.domain.model.OrganizationMembership;
import com.ailearning.platform.organization.domain.policy.OrganizationMembershipPolicy;
import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OrganizationService implements OrganizationUseCase {
    private static final int MAXIMUM_LIMIT = 100;

    private final UserLookup users;
    private final OrganizationStore organizations;
    private final OrganizationMembershipPolicy membershipPolicy;
    private final Clock clock;

    public OrganizationService(
            UserLookup users,
            OrganizationStore organizations,
            OrganizationMembershipPolicy membershipPolicy,
            Clock clock
    ) {
        this.users = Objects.requireNonNull(users, "users is required");
        this.organizations = Objects.requireNonNull(organizations, "organizations is required");
        this.membershipPolicy = Objects.requireNonNull(membershipPolicy, "membershipPolicy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public CreateOrganizationResult create(CreateOrganizationCommand command) {
        Objects.requireNonNull(command, "command is required");
        var replay = organizations.findByCreationKey(command.creatorId(), command.idempotencyKey());
        if (replay.isPresent()) {
            return new CreateOrganizationResult(toView(replay.orElseThrow()), false);
        }
        if (!users.exists(command.creatorId())) {
            throw new BusinessException(
                    "user_not_found",
                    ErrorType.NOT_FOUND,
                    "Không tìm thấy người dùng."
            );
        }

        OrganizationSlug slug = new OrganizationSlug(command.slug());
        if (organizations.existsBySlug(slug)) {
            throw slugConflict();
        }

        Instant now = clock.instant();
        Organization organization = new Organization(
                UUID.randomUUID(),
                slug,
                command.name(),
                command.creatorId(),
                command.idempotencyKey(),
                now
        );
        OrganizationMembership owner = new OrganizationMembership(
                UUID.randomUUID(),
                organization.id(),
                command.creatorId(),
                OrganizationRole.OWNER,
                now
        );
        OrganizationCreation stored = organizations.insertWithOwnerOrGet(organization, owner)
                .orElseThrow(this::slugConflict);
        return new CreateOrganizationResult(toView(stored.details()), stored.created());
    }

    @Override
    public List<OrganizationView> findMine(UUID userId, int limit) {
        Objects.requireNonNull(userId, "userId is required");
        ensureValidLimit(limit);
        return organizations.findByMember(userId, limit).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public List<OrganizationMemberView> findMembers(
            UUID requesterId,
            UUID organizationId,
            int limit
    ) {
        Objects.requireNonNull(requesterId, "requesterId is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        ensureValidLimit(limit);
        OrganizationMembership requester = organizations.findMembership(organizationId, requesterId)
                .orElseThrow(() -> new BusinessException(
                        "organization_not_found",
                        ErrorType.NOT_FOUND,
                        "Không tìm thấy tổ chức."
                ));
        membershipPolicy.ensureCanViewMemberships(requester.role());
        return organizations.findMembers(organizationId, limit).stream()
                .map(this::toMemberView)
                .toList();
    }

    private void ensureValidLimit(int limit) {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new BusinessException(
                    "invalid_organization_limit",
                    ErrorType.BAD_REQUEST,
                    "Giới hạn phải từ 1 đến 100."
            );
        }
    }

    private BusinessException slugConflict() {
        return new BusinessException(
                "organization_slug_taken",
                ErrorType.CONFLICT,
                "Định danh tổ chức đã được sử dụng."
        );
    }

    private OrganizationView toView(OrganizationMembershipDetails details) {
        return new OrganizationView(
                details.organization().id(),
                details.organization().slug().value(),
                details.organization().name(),
                details.membership().role().name(),
                details.organization().createdAt(),
                details.membership().joinedAt()
        );
    }

    private OrganizationMemberView toMemberView(OrganizationMembership membership) {
        return new OrganizationMemberView(
                membership.userId(),
                membership.role().name(),
                membership.joinedAt()
        );
    }
}
