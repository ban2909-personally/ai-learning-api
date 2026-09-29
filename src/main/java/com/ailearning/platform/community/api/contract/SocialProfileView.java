package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.identity.api.contract.SocialProfileDetails;

public record SocialProfileView(SocialProfileDetails profile, FriendshipSummary friendship) {}
