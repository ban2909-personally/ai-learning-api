package com.ailearning.platform.community.application.port.out;

import com.ailearning.platform.community.api.contract.SpaceMessageView;

import java.util.List;
import java.util.UUID;

public interface SpaceChatStore {
    List<SpaceMessageView> messages(UUID actor, UUID spaceId, Long after, Long before, int limit);

    SpaceMessageView send(UUID actor, UUID spaceId, UUID clientId, String body);

    boolean remove(UUID actor, UUID spaceId, UUID messageId);
}
