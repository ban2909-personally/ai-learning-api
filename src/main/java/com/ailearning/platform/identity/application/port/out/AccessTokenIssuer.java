package com.ailearning.platform.identity.application.port.out;

import com.ailearning.platform.identity.domain.model.User;

public interface AccessTokenIssuer {
    String issue(User user);
}
