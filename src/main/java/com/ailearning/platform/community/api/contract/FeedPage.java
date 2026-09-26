package com.ailearning.platform.community.api.contract;

import java.util.List;

public record FeedPage(List<PostView> posts, String nextCursor) {
    public FeedPage {
        posts = List.copyOf(posts);
    }
}
