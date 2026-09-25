package com.ailearning.platform.identity.application.service.impl;

import com.ailearning.platform.identity.api.contract.AccountPage;
import com.ailearning.platform.identity.api.usecase.AccountManagementUseCase;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.application.port.out.AccountAdministrationStore;
import com.ailearning.platform.identity.application.port.out.PasswordCodec;
import com.ailearning.platform.identity.domain.model.ManagedAccount;
import com.ailearning.platform.identity.domain.policy.AccountRolePolicy;
import com.ailearning.platform.identity.domain.policy.PasswordPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Locale;
import java.util.UUID;

public class AccountManagementService implements AccountManagementUseCase {
    private final AccountAdministrationStore store;
    private final AccountAccess access;
    private final PasswordCodec passwords;
    private final AccountRolePolicy roles = new AccountRolePolicy();

    public AccountManagementService(
            AccountAdministrationStore store, AccountAccess access, PasswordCodec passwords) {
        this.store = store;
        this.access = access;
        this.passwords = passwords;
    }

    private void requireAdministrator(UUID actor) {
        if (!access.requireActive(actor).roles().contains("ADMIN")) {
            throw new BusinessException(
                    "admin_required",
                    ErrorType.FORBIDDEN,
                    "Chỉ quản trị viên được quản lý tài khoản.");
        }
    }

    @Override
    public java.util.Map<String, Long> statistics(UUID actor) {
        requireAdministrator(actor);
        return store.statistics();
    }

    @Override
    public AccountPage list(UUID actor, String search, String role, int page, int size) {
        requireAdministrator(actor);
        return store.list(search.trim(), role, Math.max(0, page), Math.max(1, Math.min(size, 50)));
    }

    @Override
    public ManagedAccount create(
            UUID actor, String email, String displayName, String password, String role) {
        requireAdministrator(actor);
        roles.validate(role, "ACTIVE");
        new PasswordPolicy().validate(password);
        return store.create(
                email.trim().toLowerCase(Locale.ROOT),
                displayName.trim(),
                passwords.encode(password),
                role);
    }

    @Override
    public void update(UUID actor, UUID accountId, String role, String status) {
        requireAdministrator(actor);
        roles.validate(role, status);
        store.lockAdministrators();
        requireAdministrator(actor);
        var target =
                store.find(accountId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "account_not_found",
                                                ErrorType.NOT_FOUND,
                                                "Không tìm thấy tài khoản."));
        roles.protectLastAdministrator(
                target.roles().contains("ADMIN") && "ACTIVE".equals(target.status()),
                store.activeAdministrators(),
                role,
                status);
        store.update(accountId, role, status);
    }
}
