package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.FriendPage;
import com.ailearning.platform.community.api.contract.FriendshipSummary;
import com.ailearning.platform.community.api.contract.SocialProfileView;
import com.ailearning.platform.community.api.usecase.SocialProfileUseCase;
import com.ailearning.platform.community.application.port.out.FriendshipStore;
import com.ailearning.platform.community.domain.policy.FriendshipPolicy;
import com.ailearning.platform.identity.api.contract.ProfileUpdate;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.api.usecase.access.SocialProfileAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Set;
import java.util.UUID;

public class SocialProfileService implements SocialProfileUseCase {
    private final FriendshipStore store;
    private final SocialProfileAccess profiles;
    private final AccountAccess accounts;
    private final FriendshipPolicy policy = new FriendshipPolicy();

    public SocialProfileService(
            FriendshipStore store, SocialProfileAccess profiles, AccountAccess accounts) {
        this.store = store;
        this.profiles = profiles;
        this.accounts = accounts;
    }

    private void actor(UUID actor) {
        if (actor == null)
            throw new BusinessException(
                    "login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập để kết nối.");
        accounts.requireActive(actor);
    }

    private void pair(UUID actor, UUID peer) {
        actor(actor);
        policy.requireDifferentUsers(actor, peer);
        profiles.profile(peer);
    }

    @Override
    public SocialProfileView profile(UUID viewer, UUID person) {
        if (viewer != null) actor(viewer);
        var details = profiles.profile(person);
        return new SocialProfileView(details, store.summary(viewer, person));
    }

    @Override
    public SocialProfileView update(UUID actor, ProfileUpdate update) {
        actor(actor);
        var details = profiles.update(actor, update);
        return new SocialProfileView(details, store.summary(actor, actor));
    }

    @Override
    public FriendPage connections(UUID actor, String filter, int page) {
        actor(actor);
        if (!Set.of("accepted", "incoming", "outgoing").contains(filter == null ? "" : filter)
                || page < 0
                || page > 100)
            throw new BusinessException(
                    "invalid_friend_filter", ErrorType.BAD_REQUEST, "Bộ lọc bạn bè không hợp lệ.");
        return store.connections(actor, filter, page);
    }

    @Override
    public FriendshipSummary request(UUID actor, UUID peer) {
        pair(actor, peer);
        store.request(actor, peer);
        return store.summary(actor, peer);
    }

    @Override
    public FriendshipSummary accept(UUID actor, UUID peer) {
        pair(actor, peer);
        store.accept(actor, peer);
        return store.summary(actor, peer);
    }

    @Override
    public FriendshipSummary remove(UUID actor, UUID peer) {
        pair(actor, peer);
        store.remove(actor, peer);
        return store.summary(actor, peer);
    }
}
