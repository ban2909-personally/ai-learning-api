package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.CommentView;
import com.ailearning.platform.community.api.contract.CreatePostCommand;
import com.ailearning.platform.community.api.contract.CreateSpaceCommand;
import com.ailearning.platform.community.api.contract.FeedPage;
import com.ailearning.platform.community.api.contract.MemberView;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.contract.SpaceView;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.domain.model.Comment;
import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.MemberStatus;
import com.ailearning.platform.community.domain.model.Membership;
import com.ailearning.platform.community.domain.model.Post;
import com.ailearning.platform.community.domain.model.Space;
import com.ailearning.platform.community.domain.model.SpaceVisibility;
import com.ailearning.platform.community.domain.policy.CommunityPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class CommunityService implements CommunityUseCase {
    private final CommunityStore store;
    private final AccountAccess accounts;
    private final Clock clock;
    private final CommunityPolicy policy = new CommunityPolicy();

    public CommunityService(CommunityStore store, AccountAccess accounts, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.clock = clock;
    }

    private UUID activeViewer(UUID viewer) {
        if (viewer != null) accounts.requireActive(viewer);
        return viewer;
    }

    private UUID actor(UUID actor) {
        if (actor == null) {
            throw new BusinessException(
                    "login_required", ErrorType.UNAUTHORIZED, "Hãy đăng nhập để tương tác.");
        }
        return activeViewer(actor);
    }

    private Space spaceRequired(UUID id) {
        return store.findSpace(id).orElseThrow(() -> missing("Không tìm thấy cộng đồng."));
    }

    private Post postRequired(UUID id) {
        return store.findPost(id)
                .filter(Post::active)
                .orElseThrow(() -> missing("Không tìm thấy bài viết."));
    }

    private Membership member(UUID spaceId, UUID userId) {
        return userId == null ? null : store.membership(spaceId, userId).orElse(null);
    }

    private SpaceView view(UUID id, UUID viewer) {
        return store.spaceView(id, viewer).orElseThrow(() -> missing("Không tìm thấy cộng đồng."));
    }

    private PostView postView(UUID id, UUID viewer) {
        return store.postView(id, viewer).orElseThrow(() -> missing("Không tìm thấy bài viết."));
    }

    @Override
    public FeedPage feed(UUID viewer, UUID spaceId, String cursor, int size) {
        activeViewer(viewer);
        if (spaceId != null) {
            Space space = spaceRequired(spaceId);
            policy.requireVisible(space, member(spaceId, viewer));
        }
        int limit = Math.max(1, Math.min(size, 20));
        Instant before = null;
        UUID beforeId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                if (cursor.length() > 256) throw new IllegalArgumentException("Cursor too long");
                String decoded =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Invalid cursor");
                before = Instant.parse(parts[0]);
                beforeId = UUID.fromString(parts[1]);
            } catch (RuntimeException exception) {
                throw new BusinessException(
                        "invalid_feed_cursor",
                        ErrorType.BAD_REQUEST,
                        "Trang bài viết không hợp lệ.");
            }
        }
        List<PostView> rows = store.feed(viewer, spaceId, before, beforeId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<PostView> posts = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        String next = null;
        if (hasMore) {
            PostView last = posts.get(posts.size() - 1);
            next =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    (last.createdAt() + "|" + last.id())
                                            .getBytes(StandardCharsets.UTF_8));
        }
        return new FeedPage(posts, next);
    }

    @Override
    public List<SpaceView> spaces(UUID viewer, String search, int page) {
        activeViewer(viewer);
        String term = search == null ? "" : search.trim();
        if (term.length() > 120) {
            throw new BusinessException(
                    "invalid_space_search", ErrorType.BAD_REQUEST, "Từ khóa tìm kiếm quá dài.");
        }
        return store.searchSpaces(term, viewer, Math.max(0, page));
    }

    @Override
    public SpaceView space(UUID viewer, UUID id) {
        activeViewer(viewer);
        return view(id, viewer);
    }

    @Override
    public SpaceView createSpace(UUID actor, CreateSpaceCommand command) {
        actor(actor);
        policy.validateSpace(
                command.name(), command.description(), command.kind(), command.visibility());
        UUID id = UUID.randomUUID();
        store.createSpace(
                new Space(
                        id,
                        actor,
                        command.kind(),
                        command.visibility(),
                        command.name().trim(),
                        command.description().trim(),
                        Instant.now(clock)));
        return view(id, actor);
    }

    @Override
    public List<MemberView> members(UUID actor, UUID spaceId, int page) {
        actor(actor);
        spaceRequired(spaceId);
        policy.requireManager(member(spaceId, actor));
        return store.members(spaceId, Math.max(0, page));
    }

    @Override
    public SpaceView join(UUID actor, UUID spaceId) {
        actor(actor);
        Space space = spaceRequired(spaceId);
        Membership existing = member(spaceId, actor);
        if (existing != null && existing.status() == MemberStatus.INVITED) {
            store.changeMemberStatus(spaceId, actor, MemberStatus.ACTIVE);
        } else if (existing == null) {
            MemberStatus status =
                    space.visibility() == SpaceVisibility.PRIVATE
                            ? MemberStatus.PENDING
                            : MemberStatus.ACTIVE;
            store.addMembership(spaceId, actor, status, null);
        }
        return view(spaceId, actor);
    }

    @Override
    public SpaceView invite(UUID actor, UUID spaceId, String email) {
        actor(actor);
        spaceRequired(spaceId);
        policy.requireManager(member(spaceId, actor));
        if (email == null || email.isBlank() || email.length() > 254) {
            throw new BusinessException(
                    "invalid_invitation", ErrorType.BAD_REQUEST, "Email mời không hợp lệ.");
        }
        UUID target =
                accounts.findActiveByEmail(email)
                        .orElseThrow(
                                () ->
                                        missing(
                                                "Không tìm thấy tài khoản đang hoạt động với email"
                                                        + " này."))
                        .id();
        if (!store.addMembership(spaceId, target, MemberStatus.INVITED, actor)) {
            throw conflict("Tài khoản này đã có lời mời hoặc đã tham gia cộng đồng.");
        }
        return view(spaceId, actor);
    }

    @Override
    public SpaceView decide(UUID actor, UUID spaceId, UUID userId, boolean approve) {
        actor(actor);
        spaceRequired(spaceId);
        Membership manager = member(spaceId, actor);
        Membership target = member(spaceId, userId);
        policy.requireRemoval(manager, target);
        if (target.status() != MemberStatus.PENDING) {
            throw conflict("Chỉ yêu cầu đang chờ mới có thể được duyệt hoặc từ chối.");
        }
        if (approve) store.changeMemberStatus(spaceId, userId, MemberStatus.ACTIVE);
        else store.removeMember(spaceId, userId);
        return view(spaceId, actor);
    }

    @Override
    public SpaceView leave(UUID actor, UUID spaceId) {
        actor(actor);
        spaceRequired(spaceId);
        Membership current = member(spaceId, actor);
        if (current == null) return view(spaceId, actor);
        if (current.role() == MemberRole.OWNER) {
            throw conflict("Chủ cộng đồng phải chuyển quyền sở hữu trước khi rời đi.");
        }
        store.removeMember(spaceId, actor);
        return view(spaceId, actor);
    }

    @Override
    public SpaceView removeMember(UUID actor, UUID spaceId, UUID userId) {
        actor(actor);
        spaceRequired(spaceId);
        policy.requireRemoval(member(spaceId, actor), member(spaceId, userId));
        store.removeMember(spaceId, userId);
        return view(spaceId, actor);
    }

    @Override
    public SpaceView changeRole(UUID actor, UUID spaceId, UUID userId, MemberRole role) {
        actor(actor);
        spaceRequired(spaceId);
        policy.requireRoleChange(member(spaceId, actor), member(spaceId, userId), role);
        store.changeMemberRole(spaceId, userId, role);
        return view(spaceId, actor);
    }

    @Override
    public PostView post(UUID viewer, UUID id) {
        activeViewer(viewer);
        Post post = postRequired(id);
        if (post.spaceId() != null) {
            Space space = spaceRequired(post.spaceId());
            policy.requireVisible(space, member(space.id(), viewer));
        }
        return postView(id, viewer);
    }

    @Override
    public PostView createPost(UUID actor, CreatePostCommand command) {
        actor(actor);
        if (command == null)
            throw new BusinessException(
                    "invalid_post", ErrorType.BAD_REQUEST, "Bài viết không hợp lệ.");
        String body = policy.postBody(command.body(), command.sharedPostId() != null);
        Space destination = command.spaceId() == null ? null : spaceRequired(command.spaceId());
        policy.requirePosting(
                destination, destination == null ? null : member(destination.id(), actor));
        if (command.sharedPostId() != null) {
            Post original = postRequired(command.sharedPostId());
            Space originalSpace =
                    original.spaceId() == null ? null : spaceRequired(original.spaceId());
            policy.requireShareable(originalSpace);
        }
        UUID id = UUID.randomUUID();
        store.createPost(
                new Post(
                        id,
                        actor,
                        command.spaceId(),
                        command.sharedPostId(),
                        body,
                        true,
                        Instant.now(clock)));
        return postView(id, actor);
    }

    @Override
    public PostView like(UUID actor, UUID postId, boolean liked) {
        actor(actor);
        Post post = postRequired(postId);
        Space space = post.spaceId() == null ? null : spaceRequired(post.spaceId());
        policy.requireInteraction(space, space == null ? null : member(space.id(), actor));
        if (liked) store.addLike(postId, actor);
        else store.removeLike(postId, actor);
        return postView(postId, actor);
    }

    @Override
    public void removePost(UUID actor, UUID postId) {
        actor(actor);
        Post post = postRequired(postId);
        Membership manager = post.spaceId() == null ? null : member(post.spaceId(), actor);
        if (!post.authorId().equals(actor) && (manager == null || !manager.manager())) {
            throw new BusinessException(
                    "post_moderation_denied",
                    ErrorType.FORBIDDEN,
                    "Chỉ tác giả hoặc quản trị viên cộng đồng được gỡ bài viết.");
        }
        store.removePost(postId);
    }

    @Override
    public List<CommentView> comments(UUID viewer, UUID postId, int page) {
        post(viewer, postId);
        return store.comments(postId, Math.max(0, page));
    }

    @Override
    public CommentView comment(UUID actor, UUID postId, UUID parentId, String body) {
        actor(actor);
        Post post = postRequired(postId);
        Space space = post.spaceId() == null ? null : spaceRequired(post.spaceId());
        policy.requireInteraction(space, space == null ? null : member(space.id(), actor));
        String content = policy.commentBody(body);
        if (parentId != null) {
            Comment parent =
                    store.findComment(parentId)
                            .orElseThrow(() -> missing("Không tìm thấy bình luận gốc."));
            if (!parent.active() || !parent.postId().equals(postId) || parent.parentId() != null) {
                throw new BusinessException(
                        "invalid_reply",
                        ErrorType.BAD_REQUEST,
                        "Chỉ có thể trả lời trực tiếp một bình luận còn hiển thị.");
            }
        }
        UUID id = UUID.randomUUID();
        store.createComment(
                new Comment(id, postId, parentId, actor, content, true, Instant.now(clock)));
        return store.commentView(id)
                .orElseThrow(() -> missing("Không tìm thấy bình luận vừa tạo."));
    }

    @Override
    public void removeComment(UUID actor, UUID commentId) {
        actor(actor);
        Comment comment =
                store.findComment(commentId)
                        .filter(Comment::active)
                        .orElseThrow(() -> missing("Không tìm thấy bình luận."));
        Post post = postRequired(comment.postId());
        Membership manager = post.spaceId() == null ? null : member(post.spaceId(), actor);
        if (!comment.authorId().equals(actor)
                && !post.authorId().equals(actor)
                && (manager == null || !manager.manager())) {
            throw new BusinessException(
                    "comment_moderation_denied",
                    ErrorType.FORBIDDEN,
                    "Bạn không có quyền gỡ bình luận này.");
        }
        store.removeComment(commentId);
    }

    private BusinessException missing(String detail) {
        return new BusinessException("community_not_found", ErrorType.NOT_FOUND, detail);
    }

    private BusinessException conflict(String detail) {
        return new BusinessException("community_conflict", ErrorType.CONFLICT, detail);
    }
}
