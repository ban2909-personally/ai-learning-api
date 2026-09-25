package com.ailearning.platform.identity.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

class AccountRolePolicyTest {
    private final AccountRolePolicy policy = new AccountRolePolicy();

    @Test
    void acceptsSupportedRolesAndRejectsUnknownAuthority() {
        for (String role : AccountRolePolicy.ROLES)
            assertDoesNotThrow(() -> policy.validate(role, "ACTIVE"));
        assertThrows(BusinessException.class, () -> policy.validate("SUPERADMIN", "ACTIVE"));
        assertThrows(BusinessException.class, () -> policy.validate("ADMIN", "DELETED"));
    }

    @Test
    void keepsAtLeastOneActiveAdministrator() {
        assertThrows(
                BusinessException.class,
                () -> policy.protectLastAdministrator(true, 1, "STUDENT", "ACTIVE"));
        assertThrows(
                BusinessException.class,
                () -> policy.protectLastAdministrator(true, 1, "ADMIN", "DISABLED"));
        assertDoesNotThrow(() -> policy.protectLastAdministrator(true, 2, "STUDENT", "ACTIVE"));
        assertDoesNotThrow(() -> policy.protectLastAdministrator(true, 1, "ADMIN", "ACTIVE"));
    }
}
