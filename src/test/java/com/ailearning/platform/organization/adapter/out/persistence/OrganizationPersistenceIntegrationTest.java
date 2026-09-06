package com.ailearning.platform.organization.adapter.out.persistence;

import com.ailearning.platform.organization.application.port.out.OrganizationCreation;
import com.ailearning.platform.organization.application.port.out.OrganizationStore;
import com.ailearning.platform.organization.domain.enums.OrganizationRole;
import com.ailearning.platform.organization.domain.model.Organization;
import com.ailearning.platform.organization.domain.model.OrganizationMembership;
import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrganizationPersistenceIntegrationTest {
    private static final UUID FIRST_USER = UUID.randomUUID();
    private static final UUID SECOND_USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-05T11:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_organization_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrganizationStore organizations;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM organization_memberships");
        jdbc.update("DELETE FROM organizations");
    }

    @Test
    void createsTheOrganizationAndOwnerMembershipAsOneSnapshot() {
        UUID key = UUID.randomUUID();
        Organization organization = organization(FIRST_USER, key, "acme-academy", NOW);
        OrganizationMembership owner = owner(organization);

        OrganizationCreation creation = organizations.insertWithOwnerOrGet(organization, owner).orElseThrow();

        assertThat(creation.created()).isTrue();
        assertThat(creation.details().organization()).isEqualTo(organization);
        assertThat(creation.details().membership()).isEqualTo(owner);
        assertThat(organizations.findByCreationKey(FIRST_USER, key)).contains(creation.details());
        assertThat(organizations.findByCreationKey(SECOND_USER, key)).isEmpty();
        assertThat(organizations.existsBySlug(new OrganizationSlug("acme-academy"))).isTrue();
    }

    @Test
    void concurrentRetriesReturnTheSingleOrganizationAndOwner() throws Exception {
        UUID key = UUID.randomUUID();
        Organization first = organization(FIRST_USER, key, "retry-safe", NOW);
        Organization competing = organization(FIRST_USER, key, "retry-safe", NOW.plusSeconds(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> createWhenReleased(first, ready, start));
            var competingResult = executor.submit(() -> createWhenReleased(competing, ready, start));
            ready.await();
            start.countDown();

            OrganizationCreation storedFirst = firstResult.get();
            OrganizationCreation storedCompeting = competingResult.get();

            assertThat(storedCompeting.details()).isEqualTo(storedFirst.details());
            assertThat(storedFirst.created() || storedCompeting.created()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class)).isOne();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM organization_memberships",
                    Integer.class
            )).isOne();
        }
    }

    @Test
    void reportsAnAtomicSlugConflictForDifferentCreationKeys() {
        Organization first = organization(FIRST_USER, UUID.randomUUID(), "shared-slug", NOW);
        Organization competing = organization(SECOND_USER, UUID.randomUUID(), "shared-slug", NOW);

        assertThat(organizations.insertWithOwnerOrGet(first, owner(first))).isPresent();
        assertThat(organizations.insertWithOwnerOrGet(competing, owner(competing))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_memberships",
                Integer.class
        )).isOne();
    }

    @Test
    void boundsMembershipHistoryAndRosterWithoutCrossTenantLeakage() {
        Organization older = organization(FIRST_USER, UUID.randomUUID(), "older-org", NOW.minusSeconds(10));
        Organization newer = organization(FIRST_USER, UUID.randomUUID(), "newer-org", NOW);
        Organization other = organization(SECOND_USER, UUID.randomUUID(), "other-org", NOW.plusSeconds(10));
        organizations.insertWithOwnerOrGet(older, owner(older));
        organizations.insertWithOwnerOrGet(newer, owner(newer));
        organizations.insertWithOwnerOrGet(other, owner(other));
        UUID memberId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO organization_memberships (id, organization_id, user_id, role, joined_at)
                VALUES (?, ?, ?, 'MEMBER', ?)
                """,
                UUID.randomUUID(),
                newer.id(),
                memberId,
                Timestamp.from(NOW.plusSeconds(1))
        );

        assertThat(organizations.findByMember(FIRST_USER, 1))
                .singleElement()
                .satisfies(details -> assertThat(details.organization().id()).isEqualTo(newer.id()));
        assertThat(organizations.findByMember(SECOND_USER, 10))
                .singleElement()
                .satisfies(details -> assertThat(details.organization().id()).isEqualTo(other.id()));
        assertThat(organizations.findMembers(newer.id(), 10))
                .extracting(OrganizationMembership::userId)
                .containsExactly(FIRST_USER, memberId);
        assertThat(organizations.findMembership(newer.id(), memberId))
                .hasValueSatisfying(membership -> assertThat(membership.role())
                        .isEqualTo(OrganizationRole.MEMBER));
        assertThat(organizations.findMembership(older.id(), memberId)).isEmpty();
    }

    private OrganizationCreation createWhenReleased(
            Organization organization,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return organizations.insertWithOwnerOrGet(organization, owner(organization)).orElseThrow();
    }

    private Organization organization(
            UUID creatorId,
            UUID idempotencyKey,
            String slug,
            Instant createdAt
    ) {
        return new Organization(
                UUID.randomUUID(),
                new OrganizationSlug(slug),
                slug + " name",
                creatorId,
                idempotencyKey,
                createdAt
        );
    }

    private OrganizationMembership owner(Organization organization) {
        return new OrganizationMembership(
                UUID.randomUUID(),
                organization.id(),
                organization.createdBy(),
                OrganizationRole.OWNER,
                organization.createdAt()
        );
    }
}
