package com.ailearning.platform.organization.adapter.in.web.dto.response;

import com.ailearning.platform.organization.api.contract.OrganizationMemberView;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(UUID userId, String role, Instant joinedAt) {
    public static OrganizationMemberResponse from(OrganizationMemberView member) {
        return new OrganizationMemberResponse(member.userId(), member.role(), member.joinedAt());
    }
}
