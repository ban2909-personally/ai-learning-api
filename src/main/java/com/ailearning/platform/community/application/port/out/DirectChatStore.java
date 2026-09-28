package com.ailearning.platform.community.application.port.out;

import com.ailearning.platform.community.api.contract.DirectConversationView;
import com.ailearning.platform.community.api.contract.DirectInboxPage;
import com.ailearning.platform.community.api.contract.DirectMessageView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectChatStore {
    DirectConversationView start(UUID actor, UUID peer, UUID clientId, String body);

    Optional<DirectConversationView> conversation(UUID actor, UUID id);

    DirectInboxPage inbox(UUID actor, String filter, int page);

    List<DirectMessageView> messages(UUID actor, UUID id, Long after, Long before, int limit);

    DirectMessageView send(UUID actor, UUID id, UUID clientId, String body);

    boolean decide(UUID actor, UUID id, boolean accept);

    boolean markRead(UUID actor, UUID id, long sequence);
}
