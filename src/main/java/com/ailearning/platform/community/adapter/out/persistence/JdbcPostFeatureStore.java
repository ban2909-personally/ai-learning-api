package com.ailearning.platform.community.adapter.out.persistence;

import com.ailearning.platform.community.api.contract.PollOptionView;
import com.ailearning.platform.community.api.contract.PollView;
import com.ailearning.platform.community.application.port.out.PostFeatureStore;
import com.ailearning.platform.community.domain.model.PollKind;
import com.ailearning.platform.community.domain.model.PostFeatures;
import com.ailearning.platform.community.domain.model.ReactionKind;
import com.ailearning.platform.community.domain.policy.PostFeaturesPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPostFeatureStore implements PostFeatureStore {
    private final JdbcTemplate jdbc;
    private final PostFeaturesPolicy policy = new PostFeaturesPolicy();

    public JdbcPostFeatureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID id, PostFeatures features) {
        if (features == null) return;
        var appearance = features.appearance();
        if (appearance != null)
            jdbc.update(
                    "INSERT INTO"
                        + " community_post_features(post_id,attachment_url,background_color,font_color)"
                        + " VALUES (?,?,?,?)",
                    id,
                    appearance.attachmentUrl(),
                    appearance.backgroundColor(),
                    appearance.fontColor());
        var poll = features.poll();
        if (poll == null) return;
        jdbc.update(
                "INSERT INTO community_polls(post_id,kind,question,closes_at) VALUES (?,?,?,?)",
                id,
                poll.kind().name(),
                poll.question(),
                poll.closesAt() == null ? null : Timestamp.from(poll.closesAt()));
        for (int position = 0; position < poll.options().size(); position++)
            jdbc.update(
                    "INSERT INTO community_poll_options(id,post_id,label,position) VALUES"
                            + " (?,?,?,?)",
                    UUID.randomUUID(),
                    id,
                    poll.options().get(position),
                    position);
    }

    @Override
    public Map<UUID, PollView> polls(List<UUID> ids, UUID viewer) {
        if (ids.isEmpty()) return Map.of();
        var placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(viewer);
        args.addAll(ids);
        var rows =
                jdbc.query(
                        """
SELECT p.post_id,p.kind,p.question,p.closes_at,
 (p.closed_at IS NOT NULL OR p.closes_at<=CURRENT_TIMESTAMP) AS closed,
 o.id AS option_id,o.label,o.position,count(v.user_id) AS votes,
 max(CASE WHEN v.user_id=?::uuid THEN v.option_id::text END) AS mine
FROM community_polls p JOIN community_poll_options o ON o.post_id=p.post_id
LEFT JOIN community_poll_votes v ON v.post_id=p.post_id AND v.option_id=o.id
WHERE p.post_id IN (
"""
                                + placeholders
                                + ") GROUP BY p.post_id,o.id ORDER BY p.post_id,o.position",
                        (rs, row) ->
                                new PollRow(
                                        rs.getObject("post_id", UUID.class),
                                        PollKind.valueOf(rs.getString("kind")),
                                        rs.getString("question"),
                                        rs.getTimestamp("closes_at") == null
                                                ? null
                                                : rs.getTimestamp("closes_at").toInstant(),
                                        rs.getBoolean("closed"),
                                        new PollOptionView(
                                                rs.getObject("option_id", UUID.class),
                                                rs.getString("label"),
                                                rs.getLong("votes")),
                                        rs.getString("mine")),
                        args.toArray());
        Map<UUID, List<PollRow>> grouped = new LinkedHashMap<>();
        for (var row : rows)
            grouped.computeIfAbsent(row.id(), ignored -> new ArrayList<>()).add(row);
        Map<UUID, PollView> result = new HashMap<>();
        grouped.forEach(
                (id, options) -> {
                    var first = options.get(0);
                    String mine =
                            options.stream()
                                    .map(PollRow::mine)
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse(null);
                    var views = options.stream().map(PollRow::option).toList();
                    result.put(
                            id,
                            new PollView(
                                    first.kind(),
                                    first.question(),
                                    views,
                                    views.stream().mapToLong(PollOptionView::votes).sum(),
                                    mine == null ? null : UUID.fromString(mine),
                                    first.closesAt(),
                                    first.closed()));
                });
        return result;
    }

    private record PollRow(
            UUID id,
            PollKind kind,
            String question,
            Instant closesAt,
            boolean closed,
            PollOptionView option,
            String mine) {}

    private void lockPost(UUID id, UUID actor, boolean managerRequired) {
        var spaces =
                jdbc.query(
                        "SELECT space_id FROM community_posts WHERE id=?",
                        (rs, row) -> Optional.ofNullable(rs.getObject("space_id", UUID.class)),
                        id);
        if (spaces.isEmpty())
            throw new BusinessException(
                    "poll_not_found", ErrorType.NOT_FOUND, "Không tìm thấy bình chọn.");
        UUID space = spaces.get(0).orElse(null);
        if (space != null)
            jdbc.queryForObject(
                    "SELECT id FROM community_spaces WHERE id=? FOR UPDATE", UUID.class, space);
        var allowed =
                jdbc.query(
                        """
SELECT p.id FROM community_posts p
LEFT JOIN community_spaces s ON s.id=p.space_id
LEFT JOIN community_media_assets media ON media.id=p.media_id
LEFT JOIN community_members member ON member.space_id=p.space_id AND member.user_id=?
JOIN users actor ON actor.id=? AND actor.status='ACTIVE'
WHERE p.id=? AND p.status='ACTIVE' AND (p.media_id IS NULL OR media.expires_at>CURRENT_TIMESTAMP)
 AND (p.space_id IS NULL OR (member.status='ACTIVE') OR (s.visibility='PUBLIC' AND s.kind='PAGE'))
 AND (?=false OR p.author_id=? OR (member.status='ACTIVE' AND member.role IN ('OWNER','ADMIN')))
FOR UPDATE OF p
""",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        actor,
                        actor,
                        id,
                        managerRequired,
                        actor);
        if (allowed.isEmpty())
            throw new BusinessException(
                    "poll_access_denied", ErrorType.FORBIDDEN, "Quyền tương tác đã thay đổi.");
    }

    @Override
    @Transactional
    public void vote(UUID id, UUID actor, UUID optionId, Instant now) {
        lockPost(id, actor, false);
        var row =
                jdbc.query(
                        """
SELECT closed_at,closes_at FROM community_polls WHERE post_id=? FOR UPDATE
""",
                        (rs, index) ->
                                new PollState(
                                        rs.getTimestamp("closed_at") != null,
                                        rs.getTimestamp("closes_at") == null
                                                ? null
                                                : rs.getTimestamp("closes_at").toInstant()),
                        id);
        if (row.isEmpty())
            throw new BusinessException(
                    "poll_not_found", ErrorType.NOT_FOUND, "Không tìm thấy bình chọn.");
        policy.requireOpen(row.get(0).closed(), row.get(0).closesAt(), now);
        if (optionId == null) {
            jdbc.update(
                    "DELETE FROM community_poll_votes WHERE post_id=? AND user_id=?", id, actor);
            return;
        }
        Boolean belongs =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM community_poll_options WHERE post_id=? AND"
                                + " id=?)",
                        Boolean.class,
                        id,
                        optionId);
        if (!Boolean.TRUE.equals(belongs))
            throw new BusinessException(
                    "invalid_poll_option",
                    ErrorType.BAD_REQUEST,
                    "Lựa chọn không thuộc bình chọn.");
        jdbc.update(
                """
INSERT INTO community_poll_votes(post_id,user_id,option_id) VALUES (?,?,?)
ON CONFLICT(post_id,user_id) DO UPDATE SET option_id=EXCLUDED.option_id
""",
                id,
                actor,
                optionId);
    }

    private record PollState(boolean closed, Instant closesAt) {}

    @Override
    @Transactional
    public void react(UUID id, UUID actor, ReactionKind kind) {
        lockPost(id, actor, false);
        if (kind == null) {
            jdbc.update(
                    "DELETE FROM community_post_likes WHERE post_id=? AND user_id=?", id, actor);
            return;
        }
        jdbc.update(
                "INSERT INTO community_post_likes(post_id,user_id,kind) VALUES (?,?,?) ON"
                    + " CONFLICT(post_id,user_id) DO UPDATE SET kind=EXCLUDED.kind",
                id,
                actor,
                kind.name());
    }

    @Override
    @Transactional
    public void close(UUID id, UUID actor, Instant now) {
        lockPost(id, actor, true);
        jdbc.update(
                "UPDATE community_polls SET closed_at=COALESCE(closed_at,?) WHERE post_id=?",
                Timestamp.from(now),
                id);
    }
}
