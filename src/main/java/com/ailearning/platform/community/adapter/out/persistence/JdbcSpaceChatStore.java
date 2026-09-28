package com.ailearning.platform.community.adapter.out.persistence;

import com.ailearning.platform.community.api.contract.SpaceMessageView;
import com.ailearning.platform.community.application.port.out.SpaceChatStore;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcSpaceChatStore implements SpaceChatStore {
    private static final String SELECT =
            """
            SELECT message.*,author.display_name AS author_name
            FROM community_space_messages message JOIN users author ON author.id=message.author_id
            WHERE message.space_id=?
            """;
    private final JdbcTemplate jdbc;

    public JdbcSpaceChatStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SpaceMessageView> messages(
            UUID actor, UUID spaceId, Long after, Long before, int limit) {
        String query =
                SELECT
                        + """
AND EXISTS (SELECT 1 FROM community_members m WHERE m.space_id=message.space_id
  AND m.user_id=? AND m.status='ACTIVE')
""";
        List<Object> args = new ArrayList<>(List.of(spaceId, actor));
        if (after != null) {
            query += " AND message.sequence>?";
            args.add(after);
        }
        if (before != null) {
            query += " AND message.sequence<?";
            args.add(before);
        }
        query +=
                after == null
                        ? " ORDER BY message.sequence DESC LIMIT ?"
                        : " ORDER BY message.sequence LIMIT ?";
        args.add(limit);
        return jdbc.query(query, this::message, args.toArray());
    }

    @Override
    @Transactional
    public SpaceMessageView send(UUID actor, UUID spaceId, UUID clientId, String body) {
        jdbc.queryForObject(
                "SELECT id FROM community_spaces WHERE id=? FOR UPDATE", UUID.class, spaceId);
        Boolean active =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM community_members WHERE space_id=? AND"
                                + " user_id=? AND status='ACTIVE')",
                        Boolean.class,
                        spaceId,
                        actor);
        if (!Boolean.TRUE.equals(active))
            throw new BusinessException(
                    "space_membership_required",
                    ErrorType.FORBIDDEN,
                    "Quyền trò chuyện đã thay đổi.");
        jdbc.update(
                """
                INSERT INTO community_space_messages(id,space_id,author_id,client_id,body)
                VALUES (?,?,?,?,?) ON CONFLICT(space_id,author_id,client_id) DO NOTHING
                """,
                UUID.randomUUID(),
                spaceId,
                actor,
                clientId,
                body);
        SpaceMessageView result =
                jdbc.query(
                                SELECT + " AND message.author_id=? AND message.client_id=?",
                                this::message,
                                spaceId,
                                actor,
                                clientId)
                        .get(0);
        // Compare stored original body, even if it has been moderated: retries never resurrect it.
        String original =
                jdbc.queryForObject(
                        "SELECT body FROM community_space_messages WHERE id=?",
                        String.class,
                        result.id());
        if (!body.equals(original))
            throw new BusinessException(
                    "chat_idempotency_conflict",
                    ErrorType.CONFLICT,
                    "Mã gửi tin đã được dùng cho nội dung khác.");
        return result;
    }

    @Override
    public boolean remove(UUID actor, UUID spaceId, UUID messageId) {
        return jdbc.update(
                        """
UPDATE community_space_messages message SET status='REMOVED'
WHERE message.space_id=? AND message.id=?
  AND EXISTS (SELECT 1 FROM community_members m WHERE m.space_id=message.space_id
    AND m.user_id=? AND m.status='ACTIVE'
    AND (message.author_id=? OR m.role IN ('OWNER','ADMIN')))
""",
                        spaceId,
                        messageId,
                        actor,
                        actor)
                == 1;
    }

    private SpaceMessageView message(ResultSet rs, int row) throws SQLException {
        boolean removed = "REMOVED".equals(rs.getString("status"));
        return new SpaceMessageView(
                rs.getObject("id", UUID.class),
                rs.getLong("sequence"),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                removed ? "" : rs.getString("body"),
                removed,
                rs.getTimestamp("created_at").toInstant());
    }
}
