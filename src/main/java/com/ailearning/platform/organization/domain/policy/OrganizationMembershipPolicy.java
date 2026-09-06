package com.ailearning.platform.organization.domain.policy;

import com.ailearning.platform.organization.domain.enums.OrganizationRole;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Objects;

public class OrganizationMembershipPolicy {
    public void ensureCanViewMemberships(OrganizationRole role) {
        Objects.requireNonNull(role, "role is required");
        if (!role.canViewMemberships()) {
            throw new BusinessException(
                    "organization_members_forbidden",
                    ErrorType.FORBIDDEN,
                    "Bạn không có quyền xem danh sách thành viên của tổ chức."
            );
        }
    }
}
