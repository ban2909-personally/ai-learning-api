package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.api.usecase.PollUseCase;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.application.port.out.PostFeatureStore;
import com.ailearning.platform.community.domain.model.PostStatus;
import com.ailearning.platform.community.domain.policy.CommunityPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class PollService implements PollUseCase {
    private final PostFeatureStore features;
    private final CommunityStore store;
    private final CommunityUseCase community;
    private final AccountAccess accounts;
    private final Clock clock;
    private final CommunityPolicy policy = new CommunityPolicy();

    public PollService(
            PostFeatureStore features,
            CommunityStore store,
            CommunityUseCase community,
            AccountAccess accounts,
            Clock clock) {
        this.features = features;
        this.store = store;
        this.community = community;
        this.accounts = accounts;
        this.clock = clock;
    }

    private PostView authorize(UUID actor, UUID id) {
        if (actor == null)
            throw new BusinessException(
                    "login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập để bình chọn.");
        accounts.requireActive(actor);
        PostView post = community.post(actor, id);
        if (post.status() != PostStatus.ACTIVE || post.poll() == null)
            throw new BusinessException(
                    "poll_not_found",
                    ErrorType.NOT_FOUND,
                    "Không tìm thấy bình chọn đang hiển thị.");
        return post;
    }

    @Override
    public PostView vote(UUID actor, UUID id, UUID optionId) {
        PostView post = authorize(actor, id);
        if (post.spaceId() != null)
            policy.requireInteraction(
                    store.findSpace(post.spaceId()).orElseThrow(),
                    store.membership(post.spaceId(), actor).orElse(null));
        features.vote(id, actor, optionId, Instant.now(clock));
        return community.post(actor, id);
    }

    @Override
    public PostView close(UUID actor, UUID id) {
        PostView post = authorize(actor, id);
        if (!post.authorId().equals(actor))
            policy.requireManager(
                    post.spaceId() == null
                            ? null
                            : store.membership(post.spaceId(), actor).orElse(null));
        features.close(id, actor, Instant.now(clock));
        return community.post(actor, id);
    }
}
