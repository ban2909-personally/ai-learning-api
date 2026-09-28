package com.ailearning.platform.community.api.contract;

import java.util.List;

public record DirectMessagePage(
        List<DirectMessageView> messages,
        long oldestSequence,
        long newestSequence,
        boolean hasMore) {}
