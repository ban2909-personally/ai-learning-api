package com.ailearning.platform.identity.api.usecase.access;

import com.ailearning.platform.identity.api.contract.ProfileUpdate;
import com.ailearning.platform.identity.api.contract.SocialProfileDetails;

import java.util.UUID;

public interface SocialProfileAccess {
    SocialProfileDetails profile(UUID id);

    SocialProfileDetails update(UUID actor, ProfileUpdate update);
}
