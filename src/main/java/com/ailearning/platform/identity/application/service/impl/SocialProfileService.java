package com.ailearning.platform.identity.application.service.impl;

import com.ailearning.platform.identity.api.contract.ProfileUpdate;
import com.ailearning.platform.identity.api.contract.SocialProfileDetails;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.api.usecase.access.SocialProfileAccess;
import com.ailearning.platform.identity.application.port.out.SocialProfileStore;
import com.ailearning.platform.identity.domain.valueobject.ProfileInformation;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.UUID;

public class SocialProfileService implements SocialProfileAccess {
    private final SocialProfileStore store;
    private final AccountAccess accounts;

    public SocialProfileService(SocialProfileStore store, AccountAccess accounts) {
        this.store = store;
        this.accounts = accounts;
    }

    @Override
    public SocialProfileDetails profile(UUID id) {
        return store.findActive(id).orElseThrow(this::missing);
    }

    @Override
    public SocialProfileDetails update(UUID actor, ProfileUpdate update) {
        if (actor == null)
            throw new BusinessException("login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập.");
        accounts.requireActive(actor);
        ProfileInformation information =
                new ProfileInformation(
                        update.bio(), update.location(), update.website(), update.coverTheme());
        if (!store.save(actor, information)) throw missing();
        return profile(actor);
    }

    private BusinessException missing() {
        return new BusinessException(
                "profile_not_found", ErrorType.NOT_FOUND, "Không tìm thấy hồ sơ công khai.");
    }
}
