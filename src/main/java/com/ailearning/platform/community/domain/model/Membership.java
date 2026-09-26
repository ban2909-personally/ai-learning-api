package com.ailearning.platform.community.domain.model;

import java.util.UUID;

public record Membership(UUID spaceId, UUID userId, MemberRole role, MemberStatus status) {
    public boolean active() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean manager() {
        return active() && (role == MemberRole.OWNER || role == MemberRole.ADMIN);
    }
}
