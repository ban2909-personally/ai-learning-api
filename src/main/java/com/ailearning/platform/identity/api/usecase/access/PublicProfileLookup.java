package com.ailearning.platform.identity.api.usecase.access;

import com.ailearning.platform.identity.api.contract.PublicProfile;

import java.util.List;
import java.util.UUID;

public interface PublicProfileLookup {
    List<PublicProfile> search(String query, int page);

    PublicProfile profile(UUID id);
}
