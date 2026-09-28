package com.ailearning.platform.community.api.contract;

import java.util.List;

public record DirectInboxPage(
        List<DirectConversationView> conversations,
        long totalUnread,
        long requestCount,
        Integer nextPage) {}
