package com.ailearning.platform.community.api.contract;

import java.util.List;

public record SpaceChatPage(
        List<SpaceMessageView> messages,
        long oldestSequence,
        long newestSequence,
        boolean hasMore) {}
