package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.identity.api.contract.PublicProfile;

import java.time.Instant;

public record FriendConnection(PublicProfile person, String relationship, Instant updatedAt) {}
