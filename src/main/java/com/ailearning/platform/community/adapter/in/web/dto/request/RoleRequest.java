package com.ailearning.platform.community.adapter.in.web.dto.request;

import com.ailearning.platform.community.domain.model.MemberRole;

import jakarta.validation.constraints.NotNull;

public record RoleRequest(@NotNull MemberRole role) {}
