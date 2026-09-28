package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.SpaceChatPage;
import com.ailearning.platform.community.api.contract.SpaceMessageView;

import java.util.UUID;

public interface SpaceChatUseCase {
    SpaceChatPage messages(UUID actor, UUID spaceId, Long after, Long before);

    SpaceMessageView send(UUID actor, UUID spaceId, UUID clientId, String body);

    void remove(UUID actor, UUID spaceId, UUID messageId);
}
