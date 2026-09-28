package com.ailearning.platform.community.adapter.out.persistence;

import com.ailearning.platform.community.api.contract.CommentView;
import com.ailearning.platform.community.api.contract.MediaView;
import com.ailearning.platform.community.api.contract.MemberView;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.contract.SpaceView;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.community.application.port.out.PostFeatureStore;
import com.ailearning.platform.community.domain.model.Comment;
import com.ailearning.platform.community.domain.model.MediaAsset;
import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.MemberStatus;
import com.ailearning.platform.community.domain.model.Membership;
import com.ailearning.platform.community.domain.model.Post;
import com.ailearning.platform.community.domain.model.PostFeatures;
import com.ailearning.platform.community.domain.model.PostStatus;
import com.ailearning.platform.community.domain.model.ReactionKind;
import com.ailearning.platform.community.domain.model.Space;
import com.ailearning.platform.community.domain.model.SpaceKind;
import com.ailearning.platform.community.domain.model.SpaceVisibility;
import com.ailearning.platform.community.domain.valueobject.PostAppearance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommunityStore implements CommunityStore {
    private static final String SPACE_SELECT =
            """
            SELECT s.*, owner.display_name AS owner_name,
              (SELECT count(*) FROM community_members members
               WHERE members.space_id=s.id AND members.status='ACTIVE') AS member_count,
              mine.role AS my_role, mine.status AS my_status
            FROM community_spaces s
            JOIN users owner ON owner.id=s.owner_id
            LEFT JOIN community_members mine ON mine.space_id=s.id AND mine.user_id=?::uuid
            """;

    private static final String POST_SELECT =
            """
SELECT p.id,p.author_id,author.display_name AS author_name,p.space_id,
  space.name AS space_name,p.body,p.shared_post_id,
  CASE WHEN (original.status='ACTIVE' AND (original.media_id IS NULL OR shared_media.expires_at>CURRENT_TIMESTAMP)) THEN original.body ELSE NULL END AS shared_body,
  CASE WHEN (original.status='ACTIVE' AND (original.media_id IS NULL OR shared_media.expires_at>CURRENT_TIMESTAMP)) THEN original_author.display_name ELSE NULL END
    AS shared_author_name,
  p.created_at,p.status,
  f.attachment_url,f.background_color,f.font_color,media.expires_at AS media_expires_at,
  media.id AS media_id,media.content_type AS media_type,media.size_bytes AS media_size,
  CASE WHEN (original.status='ACTIVE' AND (original.media_id IS NULL OR shared_media.expires_at>CURRENT_TIMESTAMP)) THEN shared_media.id ELSE NULL END AS shared_media_id,
  shared_media.content_type AS shared_media_type,shared_media.size_bytes AS shared_media_size,
  (SELECT count(*) FROM community_post_likes likes WHERE likes.post_id=p.id) AS like_count,
  (SELECT count(*) FROM community_comments comments
   WHERE comments.post_id=p.id AND comments.status='ACTIVE') AS comment_count,
  (SELECT count(*) FROM community_posts shares
   WHERE shares.shared_post_id=p.id AND shares.status='ACTIVE') AS share_count,
  (SELECT kind FROM community_post_likes mine
         WHERE mine.post_id=p.id AND mine.user_id=?::uuid) AS viewer_reaction,
  (p.status='ACTIVE' AND (p.space_id IS NULL OR space.visibility='PUBLIC')) AS shareable
FROM community_posts p
JOIN users author ON author.id=p.author_id
LEFT JOIN community_spaces space ON space.id=p.space_id
LEFT JOIN community_posts original ON original.id=p.shared_post_id
LEFT JOIN users original_author ON original_author.id=original.author_id
LEFT JOIN community_media_assets media ON media.id=p.media_id
LEFT JOIN community_media_assets shared_media ON shared_media.id=original.media_id
LEFT JOIN community_post_features f ON f.post_id=p.id
""";

    private static final String COMMENT_SELECT =
            """
            SELECT comment.id,comment.post_id,comment.parent_id,comment.author_id,
              author.display_name AS author_name,
              CASE WHEN comment.status='ACTIVE' THEN comment.body ELSE '' END AS body,
              comment.status='REMOVED' AS removed,comment.created_at
            FROM community_comments comment
            JOIN users author ON author.id=comment.author_id
            """;

    private final JdbcTemplate jdbc;
    private final PostFeatureStore features;

    public JdbcCommunityStore(JdbcTemplate jdbc, PostFeatureStore features) {
        this.jdbc = jdbc;
        this.features = features;
    }

    @Override
    public Optional<Space> findSpace(UUID id) {
        return jdbc.query("SELECT * FROM community_spaces WHERE id=?", this::space, id).stream()
                .findFirst();
    }

    @Override
    public Optional<SpaceView> spaceView(UUID id, UUID viewer) {
        return jdbc.query(SPACE_SELECT + " WHERE s.id=?", this::spaceView, viewer, id).stream()
                .findFirst();
    }

    @Override
    public List<SpaceView> searchSpaces(String search, UUID viewer, int page) {
        return jdbc.query(
                SPACE_SELECT
                        + """
                           WHERE lower(s.name) LIKE ?
                           ORDER BY s.created_at DESC,s.id DESC LIMIT 20 OFFSET ?
                          """,
                this::spaceView,
                viewer,
                "%" + search.toLowerCase(java.util.Locale.ROOT) + "%",
                (long) page * 20);
    }

    @Override
    @Transactional
    public void createSpace(Space space) {
        jdbc.update(
                """
INSERT INTO community_spaces(id,owner_id,kind,visibility,name,description,created_at,updated_at)
VALUES (?,?,?,?,?,?,?,?)
""",
                space.id(),
                space.ownerId(),
                space.kind().name(),
                space.visibility().name(),
                space.name(),
                space.description(),
                Timestamp.from(space.createdAt()),
                Timestamp.from(space.createdAt()));
        jdbc.update(
                """
                INSERT INTO community_members(space_id,user_id,role,status,joined_at)
                VALUES (?,?,'OWNER','ACTIVE',?)
                """,
                space.id(),
                space.ownerId(),
                Timestamp.from(space.createdAt()));
    }

    @Override
    public Optional<Membership> membership(UUID spaceId, UUID userId) {
        return jdbc
                .query(
                        "SELECT * FROM community_members WHERE space_id=? AND user_id=?",
                        (rs, row) ->
                                new Membership(
                                        spaceId,
                                        userId,
                                        MemberRole.valueOf(rs.getString("role")),
                                        MemberStatus.valueOf(rs.getString("status"))),
                        spaceId,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public List<MemberView> members(UUID spaceId, int page) {
        return jdbc.query(
                """
SELECT members.*,users.display_name,users.email
FROM community_members members JOIN users ON users.id=members.user_id
WHERE members.space_id=?
ORDER BY CASE members.status WHEN 'PENDING' THEN 0 WHEN 'INVITED' THEN 1 ELSE 2 END,
         members.created_at,members.user_id
LIMIT 50 OFFSET ?
""",
                (rs, row) ->
                        new MemberView(
                                rs.getObject("user_id", UUID.class),
                                rs.getString("display_name"),
                                rs.getString("email"),
                                MemberRole.valueOf(rs.getString("role")),
                                MemberStatus.valueOf(rs.getString("status")),
                                rs.getTimestamp("created_at").toInstant()),
                spaceId,
                (long) page * 50);
    }

    @Override
    public boolean addMembership(UUID spaceId, UUID userId, MemberStatus status, UUID invitedBy) {
        return jdbc.update(
                        """
INSERT INTO community_members(space_id,user_id,role,status,invited_by,joined_at)
VALUES (?,?,'MEMBER',?,?,CASE WHEN ?='ACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END)
ON CONFLICT(space_id,user_id) DO NOTHING
""",
                        spaceId,
                        userId,
                        status.name(),
                        invitedBy,
                        status.name())
                == 1;
    }

    @Override
    @Transactional
    public void changeMemberStatus(UUID spaceId, UUID userId, MemberStatus status) {
        lockSpace(spaceId);
        jdbc.update(
                """
                UPDATE community_members SET status=?,
                  joined_at=CASE WHEN ?='ACTIVE' THEN CURRENT_TIMESTAMP ELSE joined_at END
                WHERE space_id=? AND user_id=? AND role<>'OWNER'
                """,
                status.name(),
                status.name(),
                spaceId,
                userId);
    }

    @Override
    @Transactional
    public void changeMemberRole(UUID spaceId, UUID userId, MemberRole role) {
        lockSpace(spaceId);
        jdbc.update(
                """
                UPDATE community_members SET role=? WHERE space_id=? AND user_id=?
                  AND status='ACTIVE' AND role<>'OWNER'
                """,
                role.name(),
                spaceId,
                userId);
    }

    @Override
    @Transactional
    public void removeMember(UUID spaceId, UUID userId) {
        lockSpace(spaceId);
        jdbc.update(
                "DELETE FROM community_members WHERE space_id=? AND user_id=? AND role<>'OWNER'",
                spaceId,
                userId);
    }

    @Override
    public Optional<Post> findPost(UUID id) {
        return jdbc
                .query(
                        "SELECT p.* FROM community_posts p LEFT JOIN community_media_assets media"
                                + " ON media.id=p.media_id WHERE p.id=? AND (p.media_id IS NULL OR"
                                + " media.expires_at>CURRENT_TIMESTAMP)",
                        this::post,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PostView> postView(UUID id, UUID viewer) {
        return withPreviews(
                        jdbc.query(
                                POST_SELECT
                                        + " WHERE p.id=? AND p.status<>'REMOVED' AND (p.media_id IS"
                                        + " NULL OR media.expires_at>CURRENT_TIMESTAMP)",
                                this::postView,
                                viewer,
                                id),
                        viewer)
                .stream()
                .findFirst();
    }

    @Override
    public List<PostView> feed(
            UUID viewer, UUID spaceId, Instant before, UUID beforeId, int limit) {
        StringBuilder query =
                new StringBuilder(POST_SELECT)
                        .append(
                                """
WHERE p.status='ACTIVE'
  AND (p.media_id IS NULL OR media.expires_at>CURRENT_TIMESTAMP)
  AND (p.space_id IS NULL OR space.visibility='PUBLIC'
       OR EXISTS(SELECT 1 FROM community_members visible
                 WHERE visible.space_id=p.space_id AND visible.user_id=?::uuid
                   AND visible.status='ACTIVE'))
  AND (?::uuid IS NULL OR p.space_id=?::uuid)
""");
        List<Object> parameters = new ArrayList<>();
        parameters.add(viewer);
        parameters.add(viewer);
        parameters.add(spaceId);
        parameters.add(spaceId);
        if (before != null) {
            query.append(" AND (p.created_at,p.id) < (?,?::uuid)");
            parameters.add(Timestamp.from(before));
            parameters.add(beforeId);
        }
        query.append(" ORDER BY p.created_at DESC,p.id DESC LIMIT ?");
        parameters.add(limit);
        return withPreviews(
                jdbc.query(query.toString(), this::postView, parameters.toArray()), viewer);
    }

    @Override
    @Transactional
    public boolean createPost(Post post, MediaAsset media, PostFeatures postFeatures) {
        if (post.spaceId() != null) {
            lockSpace(post.spaceId());
            Membership current = membership(post.spaceId(), post.authorId()).orElse(null);
            if (current == null || !current.active() || (post.active() && !current.manager()))
                return false;
        }
        if (media != null) {
            jdbc.update(
                    """
INSERT INTO community_media_assets(id,owner_id,object_key,content_type,size_bytes,etag,created_at,expires_at)
VALUES (?,?,?,?,?,?,?,?)
""",
                    media.id(),
                    media.ownerId(),
                    media.objectKey(),
                    media.contentType(),
                    media.sizeBytes(),
                    media.etag(),
                    Timestamp.from(post.createdAt()),
                    Timestamp.from(
                            new com.ailearning.platform.community.domain.policy
                                            .MediaRetentionPolicy()
                                    .expiresAt(post.createdAt())));
        }
        boolean created =
                jdbc.update(
                                """
INSERT INTO community_posts(id,author_id,space_id,shared_post_id,body,media_id,status,created_at,updated_at)
VALUES (?,?,?,?,?,?,?,?,?)
""",
                                post.id(),
                                post.authorId(),
                                post.spaceId(),
                                post.sharedPostId(),
                                post.body(),
                                post.mediaId(),
                                post.status().name(),
                                Timestamp.from(post.createdAt()),
                                Timestamp.from(post.createdAt()))
                        == 1;
        if (created) features.create(post.id(), postFeatures);
        return created;
    }

    @Override
    public List<PostView> pendingPosts(UUID spaceId, UUID viewer, int page) {
        return withPreviews(
                jdbc.query(
                        POST_SELECT
                                + """
WHERE p.space_id=? AND p.status='PENDING'
  AND (p.media_id IS NULL OR media.expires_at>CURRENT_TIMESTAMP)
  AND EXISTS (SELECT 1 FROM community_members m WHERE m.space_id=p.space_id
    AND m.user_id=? AND m.status='ACTIVE' AND m.role IN ('OWNER','ADMIN'))
ORDER BY p.created_at,p.id LIMIT 20 OFFSET ?
""",
                        this::postView,
                        viewer,
                        spaceId,
                        viewer,
                        (long) page * 20),
                viewer);
    }

    @Override
    @Transactional
    public boolean reviewPost(UUID spaceId, UUID postId, UUID actor, boolean approve) {
        lockSpace(spaceId);
        return jdbc.update(
                        """
UPDATE community_posts p SET status=?,updated_at=CURRENT_TIMESTAMP
WHERE p.id=? AND p.space_id=? AND p.status='PENDING'
  AND NOT EXISTS (SELECT 1 FROM community_media_assets media WHERE media.id=p.media_id AND media.expires_at<=CURRENT_TIMESTAMP)
  AND EXISTS (SELECT 1 FROM community_members m WHERE m.space_id=p.space_id
    AND m.user_id=? AND m.status='ACTIVE' AND m.role IN ('OWNER','ADMIN'))
  AND (?=false OR EXISTS (SELECT 1 FROM community_members author
    WHERE author.space_id=p.space_id AND author.user_id=p.author_id
    AND author.status='ACTIVE'))
""",
                        approve ? "ACTIVE" : "REJECTED",
                        postId,
                        spaceId,
                        actor,
                        approve)
                == 1;
    }

    private void lockSpace(UUID spaceId) {
        jdbc.queryForObject(
                "SELECT id FROM community_spaces WHERE id=? FOR UPDATE", UUID.class, spaceId);
    }

    @Override
    public Optional<MediaAsset> postMedia(UUID postId) {
        return jdbc
                .query(
                        """
                        SELECT media.* FROM community_media_assets media
                        JOIN community_posts p ON p.media_id=media.id WHERE p.id=?
                        """,
                        (rs, row) ->
                                new MediaAsset(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("owner_id", UUID.class),
                                        rs.getString("object_key"),
                                        rs.getString("content_type"),
                                        rs.getLong("size_bytes"),
                                        rs.getString("etag")),
                        postId)
                .stream()
                .findFirst();
    }

    private List<PostView> withPreviews(List<PostView> posts, UUID viewer) {
        if (posts.isEmpty()) return posts;
        Map<UUID, List<CommentView>> previews = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(posts.size(), "?"));
        String query =
                """
SELECT * FROM (
  SELECT comment.id,comment.post_id,comment.parent_id,comment.author_id,
    author.display_name AS author_name,comment.body,false AS removed,
    comment.created_at, row_number() OVER (
      PARTITION BY comment.post_id ORDER BY comment.created_at DESC,comment.id DESC
    ) AS preview_position
  FROM community_comments comment JOIN users author ON author.id=comment.author_id
  WHERE comment.status='ACTIVE' AND comment.parent_id IS NULL AND comment.post_id IN (
"""
                        + placeholders
                        + ") ) preview WHERE preview_position<=2 ORDER BY created_at,id";
        for (CommentView comment :
                jdbc.query(
                        query, this::mapCommentView, posts.stream().map(PostView::id).toArray())) {
            previews.computeIfAbsent(comment.postId(), ignored -> new ArrayList<>()).add(comment);
        }
        var polls = features.polls(posts.stream().map(PostView::id).toList(), viewer);
        Map<UUID, Map<ReactionKind, Long>> counts = new HashMap<>();
        jdbc.query(
                "SELECT post_id,kind,count(*) AS total FROM community_post_likes WHERE post_id IN ("
                        + placeholders
                        + ") GROUP BY post_id,kind",
                rs -> {
                    counts.computeIfAbsent(
                                    rs.getObject("post_id", UUID.class), ignored -> new HashMap<>())
                            .put(ReactionKind.valueOf(rs.getString("kind")), rs.getLong("total"));
                },
                posts.stream().map(PostView::id).toArray());
        return posts.stream()
                .map(
                        post ->
                                post.withCommentPreview(previews.getOrDefault(post.id(), List.of()))
                                        .withPoll(polls.get(post.id()))
                                        .withReactionCounts(
                                                counts.getOrDefault(post.id(), Map.of())))
                .toList();
    }

    @Override
    public void removePost(UUID id) {
        jdbc.update(
                "UPDATE community_posts SET status='REMOVED',updated_at=CURRENT_TIMESTAMP WHERE"
                        + " id=?",
                id);
    }

    @Override
    public void addLike(UUID postId, UUID userId) {
        setReaction(postId, userId, ReactionKind.LIKE);
    }

    @Override
    public void removeLike(UUID postId, UUID userId) {
        setReaction(postId, userId, null);
    }

    @Override
    public void setReaction(UUID postId, UUID actor, ReactionKind kind) {
        features.react(postId, actor, kind);
    }

    @Override
    public Optional<Comment> findComment(UUID id) {
        return jdbc.query("SELECT * FROM community_comments WHERE id=?", this::comment, id).stream()
                .findFirst();
    }

    @Override
    public Optional<CommentView> commentView(UUID id) {
        return jdbc.query(COMMENT_SELECT + " WHERE comment.id=?", this::mapCommentView, id).stream()
                .findFirst();
    }

    @Override
    public List<CommentView> comments(UUID postId, int page) {
        return jdbc.query(
                COMMENT_SELECT
                        + """
 WHERE comment.post_id=? ORDER BY comment.created_at,comment.id LIMIT 50 OFFSET ?
""",
                this::mapCommentView,
                postId,
                (long) page * 50);
    }

    @Override
    public void createComment(Comment comment) {
        jdbc.update(
                """
INSERT INTO community_comments(id,post_id,parent_id,author_id,body,status,created_at)
VALUES (?,?,?,?,?,'ACTIVE',?)
""",
                comment.id(),
                comment.postId(),
                comment.parentId(),
                comment.authorId(),
                comment.body(),
                Timestamp.from(comment.createdAt()));
    }

    @Override
    public void removeComment(UUID id) {
        jdbc.update("UPDATE community_comments SET status='REMOVED' WHERE id=?", id);
    }

    private Space space(ResultSet rs, int row) throws SQLException {
        return new Space(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                SpaceKind.valueOf(rs.getString("kind")),
                SpaceVisibility.valueOf(rs.getString("visibility")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant());
    }

    private SpaceView spaceView(ResultSet rs, int row) throws SQLException {
        String role = rs.getString("my_role");
        String status = rs.getString("my_status");
        return new SpaceView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                SpaceKind.valueOf(rs.getString("kind")),
                SpaceVisibility.valueOf(rs.getString("visibility")),
                rs.getObject("owner_id", UUID.class),
                rs.getString("owner_name"),
                rs.getLong("member_count"),
                role == null ? null : MemberRole.valueOf(role),
                status == null ? null : MemberStatus.valueOf(status),
                rs.getTimestamp("created_at").toInstant());
    }

    private Post post(ResultSet rs, int row) throws SQLException {
        return new Post(
                rs.getObject("id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getObject("space_id", UUID.class),
                rs.getObject("shared_post_id", UUID.class),
                rs.getString("body"),
                rs.getObject("media_id", UUID.class),
                PostStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }

    private PostView postView(ResultSet rs, int row) throws SQLException {
        return new PostView(
                rs.getObject("id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getObject("space_id", UUID.class),
                rs.getString("space_name"),
                rs.getString("body"),
                rs.getObject("shared_post_id", UUID.class),
                rs.getString("shared_body"),
                rs.getString("shared_author_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("like_count"),
                rs.getLong("comment_count"),
                rs.getLong("share_count"),
                rs.getString("viewer_reaction") != null,
                rs.getBoolean("shareable"),
                PostStatus.valueOf(rs.getString("status")),
                List.of(),
                rs.getObject("media_id", UUID.class) == null
                        ? null
                        : new MediaView(
                                rs.getObject("media_id", UUID.class),
                                rs.getString("media_type"),
                                rs.getLong("media_size")),
                rs.getObject("shared_media_id", UUID.class) == null
                        ? null
                        : new MediaView(
                                rs.getObject("shared_media_id", UUID.class),
                                rs.getString("shared_media_type"),
                                rs.getLong("shared_media_size")),
                rs.getString("attachment_url") == null && rs.getString("background_color") == null
                        ? null
                        : new PostAppearance(
                                rs.getString("attachment_url"),
                                rs.getString("background_color"),
                                rs.getString("font_color")),
                null,
                rs.getTimestamp("media_expires_at") == null
                        ? null
                        : rs.getTimestamp("media_expires_at").toInstant(),
                rs.getString("viewer_reaction") == null
                        ? null
                        : ReactionKind.valueOf(rs.getString("viewer_reaction")),
                Map.of());
    }

    private Comment comment(ResultSet rs, int row) throws SQLException {
        return new Comment(
                rs.getObject("id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("body"),
                "ACTIVE".equals(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }

    private CommentView mapCommentView(ResultSet rs, int row) throws SQLException {
        return new CommentView(
                rs.getObject("id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getString("body"),
                rs.getBoolean("removed"),
                rs.getTimestamp("created_at").toInstant());
    }
}
