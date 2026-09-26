package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.CommentView;
import com.ailearning.platform.community.api.contract.CreatePostCommand;
import com.ailearning.platform.community.api.contract.CreateSpaceCommand;
import com.ailearning.platform.community.api.contract.FeedPage;
import com.ailearning.platform.community.api.contract.MemberView;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.contract.SpaceView;
import com.ailearning.platform.community.domain.model.MemberRole;

import java.util.List;
import java.util.UUID;

public interface CommunityUseCase {
    FeedPage feed(UUID viewer, UUID spaceId, String cursor, int size);

    List<SpaceView> spaces(UUID viewer, String search, int page);

    SpaceView space(UUID viewer, UUID id);

    SpaceView createSpace(UUID actor, CreateSpaceCommand command);

    List<MemberView> members(UUID actor, UUID spaceId, int page);

    SpaceView join(UUID actor, UUID spaceId);

    SpaceView invite(UUID actor, UUID spaceId, String email);

    SpaceView decide(UUID actor, UUID spaceId, UUID userId, boolean approve);

    SpaceView leave(UUID actor, UUID spaceId);

    SpaceView removeMember(UUID actor, UUID spaceId, UUID userId);

    SpaceView changeRole(UUID actor, UUID spaceId, UUID userId, MemberRole role);

    PostView post(UUID viewer, UUID id);

    PostView createPost(UUID actor, CreatePostCommand command);

    PostView like(UUID actor, UUID postId, boolean liked);

    void removePost(UUID actor, UUID postId);

    List<CommentView> comments(UUID viewer, UUID postId, int page);

    CommentView comment(UUID actor, UUID postId, UUID parentId, String body);

    void removeComment(UUID actor, UUID commentId);
}
