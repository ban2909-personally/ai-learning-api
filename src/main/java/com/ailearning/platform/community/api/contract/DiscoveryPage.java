package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.identity.api.contract.PublicProfile;

import java.util.List;

public record DiscoveryPage(
        List<SpaceView> spaces,
        List<PublicProfile> people,
        boolean spacesHasMore,
        boolean peopleHasMore,
        int page) {}
