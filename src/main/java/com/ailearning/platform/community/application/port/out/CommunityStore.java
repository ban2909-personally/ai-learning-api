package com.ailearning.platform.community.application.port.out;

import com.ailearning.platform.community.api.contract.CommentView;
import com.ailearning.platform.community.api.contract.MemberView;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.contract.SpaceView;
import com.ailearning.platform.community.domain.model.Comment;
import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.MemberStatus;
import com.ailearning.platform.community.domain.model.Membership;
import com.ailearning.platform.community.domain.model.Post;
import com.ailearning.platform.community.domain.model.Space;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityStore {
    Optional<Space> findSpace(UUID id);

    Optional<SpaceView> spaceView(UUID id, UUID viewer);

    List<SpaceView> searchSpaces(String search, UUID viewer, int page);

    void createSpace(Space space);

    Optional<Membership> membership(UUID spaceId, UUID userId);

    List<MemberView> members(UUID spaceId, int page);

    boolean addMembership(UUID spaceId, UUID userId, MemberStatus status, UUID invitedBy);

    void changeMemberStatus(UUID spaceId, UUID userId, MemberStatus status);

    void changeMemberRole(UUID spaceId, UUID userId, MemberRole role);

    void removeMember(UUID spaceId, UUID userId);

    Optional<Post> findPost(UUID id);

    Optional<PostView> postView(UUID id, UUID viewer);

    List<PostView> feed(UUID viewer, UUID spaceId, Instant before, UUID beforeId, int limit);

    void createPost(Post post);

    void removePost(UUID id);

    void addLike(UUID postId, UUID userId);

    void removeLike(UUID postId, UUID userId);

    Optional<Comment> findComment(UUID id);

    Optional<CommentView> commentView(UUID id);

    List<CommentView> comments(UUID postId, int page);

    void createComment(Comment comment);

    void removeComment(UUID id);
}
