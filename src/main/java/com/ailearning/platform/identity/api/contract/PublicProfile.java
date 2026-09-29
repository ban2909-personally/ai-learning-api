package com.ailearning.platform.identity.api.contract;

import java.util.UUID;

/** Directory-safe identity: deliberately excludes email, roles and account status. */
public record PublicProfile(UUID id, String displayName) {}
