package com.ailearning.platform.identity.api.contract;

import java.util.Set;
import java.util.UUID;

public record UserView(UUID id, String email, String displayName, Set<String> roles) {
}
