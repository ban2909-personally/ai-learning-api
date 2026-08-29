package com.ailearning.platform.identity.api.usecase;

import com.ailearning.platform.identity.api.contract.AuthSession;
import com.ailearning.platform.identity.api.contract.LoginCommand;
import com.ailearning.platform.identity.api.contract.RegisterCommand;
import com.ailearning.platform.identity.api.contract.UserView;

import java.util.UUID;

public interface IdentityUseCase {
    AuthSession register(RegisterCommand command);
    AuthSession login(LoginCommand command);
    AuthSession refresh(String refreshToken);
    void logout(String refreshToken);
    UserView me(UUID userId);
}
