package com.ailearning.platform.organization.domain.policy;

import com.ailearning.platform.organization.domain.enums.OrganizationRole;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMembershipPolicyTest {
    private final OrganizationMembershipPolicy policy = new OrganizationMembershipPolicy();

    @Test
    void ownersAndAdminsCanViewTheRoster() {
        assertThatCode(() -> policy.ensureCanViewMemberships(OrganizationRole.OWNER))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanViewMemberships(OrganizationRole.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void membersCannotViewTheRoster() {
        assertThatThrownBy(() -> policy.ensureCanViewMemberships(OrganizationRole.MEMBER))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("organization_members_forbidden");
    }
}
