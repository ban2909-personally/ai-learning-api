package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.PostStatus;
import com.ailearning.platform.community.domain.model.ReactionKind;
import com.ailearning.platform.community.domain.valueobject.PostAppearance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        MediaView sharedMedia,
        PostAppearance appearance,
        PollView poll,
        Instant mediaExpiresAt,
        ReactionKind viewerReaction,
        Map<ReactionKind, Long> reactionCounts) {
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
                sharedMedia,
                appearance,
                poll,
                mediaExpiresAt,
                viewerReaction,
                reactionCounts);
    }

    public PostView withPoll(PollView value) {
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
                commentPreview,
                media,
                sharedMedia,
                appearance,
                value,
                mediaExpiresAt,
                viewerReaction,
                reactionCounts);
    }

    public PostView withReactionCounts(Map<ReactionKind, Long> counts) {
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
                commentPreview,
                media,
                sharedMedia,
                appearance,
                poll,
                mediaExpiresAt,
                viewerReaction,
                Map.copyOf(counts));
    }
}
