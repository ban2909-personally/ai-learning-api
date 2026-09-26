package com.ailearning.platform.identity.application.service.impl;

import com.ailearning.platform.identity.api.contract.UserView;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.application.port.out.UserStore;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class AccountAccessService implements AccountAccess {
    private final UserStore users;

    public AccountAccessService(UserStore users) {
        this.users = users;
    }

    @Override
    public UserView requireActive(UUID userId) {
        var user =
                users.findById(userId)
                        .filter(value -> value.active())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "account_unavailable",
                                                ErrorType.FORBIDDEN,
                                                "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa."));
        return new UserView(user.id(), user.email(), user.displayName(), user.roles());
    }

    @Override
    public Optional<UserView> findActiveByEmail(String email) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(user -> user.active())
                .map(
                        user ->
                                new UserView(
                                        user.id(), user.email(), user.displayName(), user.roles()));
    }
}
