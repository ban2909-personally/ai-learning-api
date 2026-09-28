package com.ailearning.platform.community.application.port.out;

import com.ailearning.platform.community.api.contract.PollView;
import com.ailearning.platform.community.domain.model.PostFeatures;
import com.ailearning.platform.community.domain.model.ReactionKind;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PostFeatureStore {
    void react(UUID postId, UUID actor, ReactionKind kind);

    void create(UUID postId, PostFeatures features);

    Map<UUID, PollView> polls(List<UUID> postIds, UUID viewer);

    void vote(UUID postId, UUID actor, UUID optionId, Instant now);

    void close(UUID postId, UUID actor, Instant now);
}
