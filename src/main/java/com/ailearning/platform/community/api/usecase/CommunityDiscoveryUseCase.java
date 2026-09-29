package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.DiscoveryPage;
import com.ailearning.platform.identity.api.contract.PublicProfile;

import java.util.UUID;

public interface CommunityDiscoveryUseCase {
    DiscoveryPage search(UUID viewer, String query, int page);

    PublicProfile profile(UUID viewer, UUID id);
}
