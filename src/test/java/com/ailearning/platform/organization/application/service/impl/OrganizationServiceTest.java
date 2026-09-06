package com.ailearning.platform.organization.application.service.impl;

import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrganizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    private UserLookup users;
    private OrganizationStore organizations;
    private OrganizationService service;

    @BeforeEach
    void setUp() {
        users = org.mockito.Mockito.mock(UserLookup.class);
        organizations = org.mockito.Mockito.mock(OrganizationStore.class);
        service = new OrganizationService(
                users,
                organizations,
                new OrganizationMembershipPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void replaysTheOriginalSnapshotWithoutRevalidatingMutableDependencies() {
        when(organizations.findByCreationKey(USER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(details(OrganizationRole.OWNER)));

        var result = service.create(command());

        assertThat(result.created()).isFalse();
        assertThat(result.organization().id()).isEqualTo(ORGANIZATION_ID);
        verifyNoInteractions(users);
        verify(organizations, never()).existsBySlug(any());
        verify(organizations, never()).insertWithOwnerOrGet(any(), any());
    }

    @Test
    void createsAnOrganizationAndOwnerMembershipAsOnePortOperation() {
        when(users.exists(USER_ID)).thenReturn(true);
        when(organizations.existsBySlug(new OrganizationSlug("acme-academy"))).thenReturn(false);
        when(organizations.insertWithOwnerOrGet(any(), any())).thenAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            OrganizationMembership owner = invocation.getArgument(1);
            return Optional.of(new OrganizationCreation(
                    new OrganizationMembershipDetails(organization, owner),
                    true
            ));
        });

        var result = service.create(command());

        ArgumentCaptor<Organization> organizationCaptor = ArgumentCaptor.forClass(Organization.class);
        ArgumentCaptor<OrganizationMembership> membershipCaptor = ArgumentCaptor.forClass(
                OrganizationMembership.class
        );
        verify(organizations).insertWithOwnerOrGet(
                organizationCaptor.capture(),
                membershipCaptor.capture()
        );
        Organization created = organizationCaptor.getValue();
        OrganizationMembership owner = membershipCaptor.getValue();
        assertThat(result.created()).isTrue();
        assertThat(result.organization().role()).isEqualTo("OWNER");
        assertThat(created.createdBy()).isEqualTo(USER_ID);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(owner.organizationId()).isEqualTo(created.id());
        assertThat(owner.userId()).isEqualTo(USER_ID);
        assertThat(owner.role()).isEqualTo(OrganizationRole.OWNER);
        assertThat(owner.joinedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsMissingCreatorsAndConflictingSlugs() {
        when(users.exists(USER_ID)).thenReturn(false);
        assertBusinessCode(() -> service.create(command()), "user_not_found");

        when(users.exists(USER_ID)).thenReturn(true);
        when(organizations.existsBySlug(new OrganizationSlug("acme-academy"))).thenReturn(true);
        assertBusinessCode(() -> service.create(command()), "organization_slug_taken");
        verify(organizations, never()).insertWithOwnerOrGet(any(), any());
    }

    @Test
    void translatesAConcurrentSlugConflictFromThePersistenceBoundary() {
        when(users.exists(USER_ID)).thenReturn(true);
        when(organizations.existsBySlug(any())).thenReturn(false);
        when(organizations.insertWithOwnerOrGet(any(), any())).thenReturn(Optional.empty());

        assertBusinessCode(() -> service.create(command()), "organization_slug_taken");
    }

    @Test
    void returnsOnlyTheRequestedUsersBoundedMemberships() {
        when(organizations.findByMember(USER_ID, 1)).thenReturn(List.of(details(OrganizationRole.ADMIN)));

        var result = service.findMine(USER_ID, 1);

        assertThat(result).singleElement().satisfies(organization -> {
            assertThat(organization.id()).isEqualTo(ORGANIZATION_ID);
            assertThat(organization.role()).isEqualTo("ADMIN");
        });
        verify(organizations).findByMember(USER_ID, 1);
        assertBusinessCode(() -> service.findMine(USER_ID, 101), "invalid_organization_limit");
    }

    @Test
    void hidesOrganizationsFromNonMembersAndForbidsRegularMembersFromTheRoster() {
        when(organizations.findMembership(ORGANIZATION_ID, USER_ID)).thenReturn(Optional.empty());
        assertBusinessCode(
                () -> service.findMembers(USER_ID, ORGANIZATION_ID, 50),
                "organization_not_found"
        );

        when(organizations.findMembership(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(membership(OrganizationRole.MEMBER)));
        assertBusinessCode(
                () -> service.findMembers(USER_ID, ORGANIZATION_ID, 50),
                "organization_members_forbidden"
        );
        verify(organizations, never()).findMembers(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void allowsOwnersToReadTheBoundedRoster() {
        OrganizationMembership owner = membership(OrganizationRole.OWNER);
        OrganizationMembership member = new OrganizationMembership(
                UUID.randomUUID(),
                ORGANIZATION_ID,
                UUID.randomUUID(),
                OrganizationRole.MEMBER,
                NOW.plusSeconds(1)
        );
        when(organizations.findMembership(ORGANIZATION_ID, USER_ID)).thenReturn(Optional.of(owner));
        when(organizations.findMembers(ORGANIZATION_ID, 2)).thenReturn(List.of(owner, member));

        var result = service.findMembers(USER_ID, ORGANIZATION_ID, 2);

        assertThat(result).extracting(view -> view.role()).containsExactly("OWNER", "MEMBER");
        verify(organizations).findMembers(ORGANIZATION_ID, 2);
    }

    private CreateOrganizationCommand command() {
        return new CreateOrganizationCommand(USER_ID, "Acme Academy", "acme-academy", IDEMPOTENCY_KEY);
    }

    private OrganizationMembershipDetails details(OrganizationRole role) {
        Organization organization = new Organization(
                ORGANIZATION_ID,
                new OrganizationSlug("acme-academy"),
                "Acme Academy",
                USER_ID,
                IDEMPOTENCY_KEY,
                NOW
        );
        return new OrganizationMembershipDetails(organization, membership(role));
    }

    private OrganizationMembership membership(OrganizationRole role) {
        return new OrganizationMembership(UUID.randomUUID(), ORGANIZATION_ID, USER_ID, role, NOW);
    }

    private void assertBusinessCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(code);
    }
}
