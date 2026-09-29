package com.ailearning.platform.community.application.port.out;

import com.ailearning.platform.community.api.contract.FriendPage;
import com.ailearning.platform.community.api.contract.FriendshipSummary;

import java.util.UUID;

public interface FriendshipStore {
    FriendshipSummary summary(UUID viewer, UUID person);

    FriendPage connections(UUID actor, String filter, int page);

    void request(UUID actor, UUID peer);

    void accept(UUID actor, UUID peer);

    void remove(UUID actor, UUID peer);
}
