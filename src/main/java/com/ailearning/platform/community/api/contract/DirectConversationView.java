package com.ailearning.platform.community.api.contract;

import java.time.Instant;
import java.util.UUID;

public record DirectConversationView(
        UUID id,
        UUID peerId,
        String peerName,
        UUID initiatorId,
        String status,
        String lastMessage,
        Instant updatedAt,
        long unreadCount,
        long readSequence) {}
