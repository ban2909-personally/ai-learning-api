package com.ailearning.platform.organization.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrganizationSlugTest {
    @Test
    void acceptsAndTrimsLowercaseUrlSafeSlugs() {
        assertThat(new OrganizationSlug("  acme-academy  ").value()).isEqualTo("acme-academy");
        assertThat(new OrganizationSlug("b2b").value()).isEqualTo("b2b");
    }

    @Test
    void rejectsAmbiguousOrUnboundedSlugs() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OrganizationSlug("Acme"));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrganizationSlug("acme_academy"));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrganizationSlug("-acme"));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrganizationSlug("ab"));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrganizationSlug("a".repeat(81)));
    }
}
