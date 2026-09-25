package com.ailearning.platform.identity.application.port.out;

import com.ailearning.platform.identity.api.contract.AccountPage;
import com.ailearning.platform.identity.domain.model.ManagedAccount;

import java.util.Optional;
import java.util.UUID;

public interface AccountAdministrationStore {
    java.util.Map<String, Long> statistics();

    AccountPage list(String search, String role, int page, int size);

    Optional<ManagedAccount> find(UUID id);

    ManagedAccount create(String email, String displayName, String passwordHash, String role);

    void lockAdministrators();

    long activeAdministrators();

    void update(UUID id, String role, String status);
}
