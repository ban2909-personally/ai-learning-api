package com.ailearning.platform.identity.api.usecase.access;

import com.ailearning.platform.identity.api.contract.UserView;

import java.util.UUID;

public interface AccountAccess {
    UserView requireActive(UUID userId);
}
