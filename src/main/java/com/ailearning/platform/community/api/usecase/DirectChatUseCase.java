package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.DirectConversationView;
import com.ailearning.platform.community.api.contract.DirectInboxPage;
import com.ailearning.platform.community.api.contract.DirectMessagePage;
import com.ailearning.platform.community.api.contract.DirectMessageView;

import java.util.Optional;
import java.util.UUID;

public interface DirectChatUseCase {
    DirectConversationView startWithPeer(UUID actor, UUID peer, UUID clientId, String body);

    Optional<DirectConversationView> findWithPeer(UUID actor, UUID peer);

    DirectConversationView start(UUID actor, String email, UUID clientId, String body);

    DirectInboxPage inbox(UUID actor, String filter, int page);

    DirectMessagePage messages(UUID actor, UUID id, Long after, Long before);

    DirectMessageView send(UUID actor, UUID id, UUID clientId, String body);

    DirectConversationView decide(UUID actor, UUID id, boolean accept);

    void markRead(UUID actor, UUID id, long sequence);
}
