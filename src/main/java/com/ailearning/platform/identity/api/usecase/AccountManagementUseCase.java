package com.ailearning.platform.identity.api.usecase;

import com.ailearning.platform.identity.api.contract.AccountPage;
import com.ailearning.platform.identity.domain.model.ManagedAccount;

import java.util.UUID;

public interface AccountManagementUseCase {
    java.util.Map<String, Long> statistics(UUID actorId);

    AccountPage list(UUID actorId, String search, String role, int page, int size);

    ManagedAccount create(
            UUID actorId, String email, String displayName, String password, String role);

    void update(UUID actorId, UUID accountId, String role, String status);
}
