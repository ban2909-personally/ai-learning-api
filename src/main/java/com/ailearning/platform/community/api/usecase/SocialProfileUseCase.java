package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.FriendPage;
import com.ailearning.platform.community.api.contract.FriendshipSummary;
import com.ailearning.platform.community.api.contract.SocialProfileView;
import com.ailearning.platform.identity.api.contract.ProfileUpdate;

import java.util.UUID;

public interface SocialProfileUseCase {
    SocialProfileView profile(UUID viewer, UUID person);

    SocialProfileView update(UUID actor, ProfileUpdate update);

    FriendPage connections(UUID actor, String filter, int page);

    FriendshipSummary request(UUID actor, UUID peer);

    FriendshipSummary accept(UUID actor, UUID peer);

    FriendshipSummary remove(UUID actor, UUID peer);
}
