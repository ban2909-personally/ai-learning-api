package com.ailearning.platform.identity.api.usecase.lookup;

import java.util.UUID;

public interface UserLookup {
    boolean exists(UUID userId);
}
