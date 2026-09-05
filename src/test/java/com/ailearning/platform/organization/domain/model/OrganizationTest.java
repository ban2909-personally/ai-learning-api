package com.ailearning.platform.organization.domain.model;

import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrganizationTest {
    @Test
    void normalizesAValidOrganizationName() {
        Organization organization = new Organization(
                UUID.randomUUID(),
                new OrganizationSlug("acme-academy"),
                "  Acme Academy  ",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-09-05T10:00:00Z")
        );

        assertThat(organization.name()).isEqualTo("Acme Academy");
    }

    @Test
    void rejectsBlankOrUnboundedNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> organization(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> organization("a".repeat(121)));
    }

    private Organization organization(String name) {
        return new Organization(
                UUID.randomUUID(),
                new OrganizationSlug("acme-academy"),
                name,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-09-05T10:00:00Z")
        );
    }
}
