package com.ailearning.platform.identity.api;

import com.ailearning.platform.identity.domain.RoleEntity;
import com.ailearning.platform.identity.domain.UserEntity;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Set<String> roles) {

    public static UserResponse from(UserEntity user) {
        Set<String> roles = new TreeSet<>();
        roles.addAll(user.getRoles().stream().map(RoleEntity::getCode).toList());
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), roles);
    }
}
