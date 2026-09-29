package com.ailearning.platform.identity.application.port.out;

import com.ailearning.platform.identity.api.contract.PublicProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicProfileStore {
    List<PublicProfile> search(String query, int page);

    Optional<PublicProfile> findActive(UUID id);
}
