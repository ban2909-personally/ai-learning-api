package com.ailearning.platform.identity.adapter.in.web.dto.response;

import com.ailearning.platform.identity.api.contract.UserView;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Set<String> roles) {

    public static UserResponse from(UserView user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), new TreeSet<>(user.roles()));
    }
}
