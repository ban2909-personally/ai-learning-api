package com.ailearning.platform.identity.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Set;

public class AccountRolePolicy {
    public static final Set<String> ROLES =
            Set.of("GUEST", "STUDENT", "LECTURE", "INSTRUCTOR", "LEADER", "ADMIN");

    public void validate(String role, String status) {
        if (!ROLES.contains(role) || !Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new BusinessException(
                    "invalid_account_role",
                    ErrorType.BAD_REQUEST,
                    "Vai trò hoặc trạng thái không hợp lệ.");
        }
    }

    public void protectLastAdministrator(
            boolean activeAdministrator,
            long activeAdministrators,
            String newRole,
            String newStatus) {
        if (activeAdministrator
                && activeAdministrators <= 1
                && (!"ADMIN".equals(newRole) || !"ACTIVE".equals(newStatus))) {
            throw new BusinessException(
                    "last_administrator",
                    ErrorType.CONFLICT,
                    "Phải giữ lại ít nhất một quản trị viên đang hoạt động.");
        }
    }
}
