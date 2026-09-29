package com.ailearning.platform.identity.application.port.out;

import com.ailearning.platform.identity.api.contract.SocialProfileDetails;
import com.ailearning.platform.identity.domain.valueobject.ProfileInformation;

import java.util.Optional;
import java.util.UUID;

public interface SocialProfileStore {
    Optional<SocialProfileDetails> findActive(UUID id);

    boolean save(UUID actor, ProfileInformation information);
}
