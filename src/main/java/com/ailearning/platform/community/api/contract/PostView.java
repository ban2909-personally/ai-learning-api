package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.PostStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostView(
        UUID id,
        UUID authorId,
        String authorName,
        UUID spaceId,
        String spaceName,
        String body,
        UUID sharedPostId,
        String sharedBody,
        String sharedAuthorName,
        Instant createdAt,
        long likeCount,
        long commentCount,
        long shareCount,
        boolean likedByViewer,
        boolean shareable,
        PostStatus status,
        List<CommentView> commentPreview,
        MediaView media,
        MediaView sharedMedia) {
    public PostView withCommentPreview(List<CommentView> comments) {
        return new PostView(
                id,
                authorId,
                authorName,
                spaceId,
                spaceName,
                body,
                sharedPostId,
                sharedBody,
                sharedAuthorName,
                createdAt,
                likeCount,
                commentCount,
                shareCount,
                likedByViewer,
                shareable,
                status,
                List.copyOf(comments),
                media,
                sharedMedia);
    }
}
