package com.ailearning.platform.community.adapter.out.persistence;

import com.ailearning.platform.community.api.contract.DirectConversationView;
import com.ailearning.platform.community.api.contract.DirectInboxPage;
import com.ailearning.platform.community.api.contract.DirectMessageView;
import com.ailearning.platform.community.application.port.out.DirectChatStore;
import com.ailearning.platform.community.domain.policy.DirectChatPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDirectChatStore implements DirectChatStore {
    private static final String CONVERSATIONS =
            """
SELECT c.id,c.initiator_id,c.status,c.updated_at,peer.id AS peer_id,peer.display_name AS peer_name,
 (SELECT body FROM community_direct_messages WHERE conversation_id=c.id ORDER BY sequence DESC LIMIT 1) AS last_message,
 (SELECT count(*) FROM community_direct_messages WHERE conversation_id=c.id AND author_id<>?
   AND sequence>CASE WHEN c.user_left=? THEN c.left_read_sequence ELSE c.right_read_sequence END) AS unread_count,
 CASE WHEN c.user_left=? THEN c.left_read_sequence ELSE c.right_read_sequence END AS read_sequence
FROM community_direct_conversations c
JOIN users peer ON peer.id=CASE WHEN c.user_left=? THEN c.user_right ELSE c.user_left END
WHERE (c.user_left=? OR c.user_right=?)
""";
    private final JdbcTemplate jdbc;
    private final DirectChatPolicy policy = new DirectChatPolicy();

    public JdbcDirectChatStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DirectConversationView> conversation(UUID actor, UUID id) {
        var args = new ArrayList<Object>(Collections.nCopies(6, actor));
        args.add(id);
        return jdbc.query(CONVERSATIONS + " AND c.id=?", this::view, args.toArray()).stream()
                .findFirst();
    }

    @Override
    public DirectInboxPage inbox(UUID actor, String filter, int page) {
        var args = new ArrayList<Object>(Collections.nCopies(6, actor));
        String query = CONVERSATIONS;
        if ("requests".equals(filter)) {
            query += " AND c.status='REQUEST' AND c.initiator_id<>?";
            args.add(actor);
        }
        if ("unread".equals(filter)) {
            query +=
                    """
 AND EXISTS(SELECT 1 FROM community_direct_messages m WHERE m.conversation_id=c.id
 AND m.author_id<>? AND m.sequence>CASE WHEN c.user_left=? THEN c.left_read_sequence ELSE c.right_read_sequence END)
""";
            args.add(actor);
            args.add(actor);
        }
        query += " ORDER BY c.updated_at DESC,c.id DESC LIMIT 21 OFFSET ?";
        args.add((long) page * 20);
        var rows = jdbc.query(query, this::view, args.toArray());
        long unread =
                jdbc.queryForObject(
                        """
SELECT count(*) FROM community_direct_messages m JOIN community_direct_conversations c ON c.id=m.conversation_id
WHERE (c.user_left=? OR c.user_right=?) AND m.author_id<>?
 AND m.sequence>CASE WHEN c.user_left=? THEN c.left_read_sequence ELSE c.right_read_sequence END
""",
                        Long.class,
                        actor,
                        actor,
                        actor,
                        actor);
        long requests =
                jdbc.queryForObject(
                        "SELECT count(*) FROM community_direct_conversations WHERE (user_left=? OR"
                                + " user_right=?) AND initiator_id<>? AND status='REQUEST'",
                        Long.class,
                        actor,
                        actor,
                        actor);
        return new DirectInboxPage(
                List.copyOf(rows.subList(0, Math.min(20, rows.size()))),
                unread,
                requests,
                rows.size() > 20 ? page + 1 : null);
    }

    private record State(UUID left, UUID right, UUID initiator, String status) {}

    private State lock(UUID actor, UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM community_direct_conversations WHERE id=? AND (user_left=?"
                                + " OR user_right=?) FOR UPDATE",
                        (rs, row) ->
                                new State(
                                        rs.getObject("user_left", UUID.class),
                                        rs.getObject("user_right", UUID.class),
                                        rs.getObject("initiator_id", UUID.class),
                                        rs.getString("status")),
                        id,
                        actor,
                        actor)
                .stream()
                .findFirst()
                .orElseThrow(this::missing);
    }

    private void requireActivePair(UUID left, UUID right) {
        long active =
                jdbc.queryForObject(
                        "SELECT count(*) FROM users WHERE id IN (?,?) AND status='ACTIVE'",
                        Long.class,
                        left,
                        right);
        if (active != 2) throw missing();
    }

    @Override
    @Transactional
    public DirectConversationView start(UUID actor, UUID peer, UUID clientId, String body) {
        UUID left = actor.toString().compareTo(peer.toString()) < 0 ? actor : peer;
        UUID right = left.equals(actor) ? peer : actor;
        requireActivePair(left, right);
        jdbc.update(
                """
INSERT INTO community_direct_conversations(id,user_left,user_right,initiator_id,status)
VALUES (?,?,?,?,'REQUEST') ON CONFLICT(user_left,user_right) DO NOTHING
""",
                UUID.randomUUID(),
                left,
                right,
                actor);
        UUID id =
                jdbc.queryForObject(
                        "SELECT id FROM community_direct_conversations WHERE user_left=? AND"
                                + " user_right=?",
                        UUID.class,
                        left,
                        right);
        State state = lock(actor, id);
        if ("REQUEST".equals(state.status())) {
            if (!state.initiator().equals(actor))
                throw conflict("Hãy chấp nhận lời mời chat trước khi gửi.");
            var existing = existing(id, actor, clientId);
            if (existing.isPresent()) compareBody(existing.get(), body);
            else {
                long count =
                        jdbc.queryForObject(
                                "SELECT count(*) FROM community_direct_messages WHERE"
                                        + " conversation_id=?",
                                Long.class,
                                id);
                if (count != 0)
                    throw conflict("Đã gửi một lời mời chat; hãy chờ người nhận chấp nhận.");
                insert(id, actor, clientId, body);
            }
        } else {
            policy.requireSend(state.status());
            insert(id, actor, clientId, body);
        }
        return conversation(actor, id).orElseThrow(this::missing);
    }

    @Override
    @Transactional
    public DirectMessageView send(UUID actor, UUID id, UUID clientId, String body) {
        State state = lock(actor, id);
        requireActivePair(state.left(), state.right());
        policy.requireSend(state.status());
        return insert(id, actor, clientId, body);
    }

    private Optional<DirectMessageView> existing(UUID id, UUID actor, UUID clientId) {
        return jdbc
                .query(
                        "SELECT * FROM community_direct_messages WHERE conversation_id=? AND"
                                + " author_id=? AND client_id=?",
                        this::message,
                        id,
                        actor,
                        clientId)
                .stream()
                .findFirst();
    }

    private void compareBody(DirectMessageView existing, String body) {
        if (!existing.body().equals(body)) throw conflict("Mã gửi đã được dùng cho tin nhắn khác.");
    }

    private DirectMessageView insert(UUID id, UUID actor, UUID clientId, String body) {
        var existing = existing(id, actor, clientId);
        if (existing.isPresent()) {
            compareBody(existing.get(), body);
            return existing.get();
        }
        jdbc.update(
                "INSERT INTO community_direct_messages(id,conversation_id,author_id,client_id,body)"
                        + " VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                actor,
                clientId,
                body);
        jdbc.update(
                "UPDATE community_direct_conversations SET updated_at=CURRENT_TIMESTAMP WHERE id=?",
                id);
        return existing(id, actor, clientId).orElseThrow(this::missing);
    }

    @Override
    public List<DirectMessageView> messages(
            UUID actor, UUID id, Long after, Long before, int limit) {
        String query =
                """
SELECT m.* FROM community_direct_messages m JOIN community_direct_conversations c ON c.id=m.conversation_id
WHERE c.id=? AND (c.user_left=? OR c.user_right=?)
""";
        var args = new ArrayList<Object>(List.of(id, actor, actor));
        if (after != null) {
            query += " AND m.sequence>?";
            args.add(after);
        }
        if (before != null) {
            query += " AND m.sequence<?";
            args.add(before);
        }
        query +=
                after == null
                        ? " ORDER BY m.sequence DESC LIMIT ?"
                        : " ORDER BY m.sequence LIMIT ?";
        args.add(limit);
        return jdbc.query(query, this::message, args.toArray());
    }

    @Override
    @Transactional
    public boolean decide(UUID actor, UUID id, boolean accept) {
        State state = lock(actor, id);
        if (!"REQUEST".equals(state.status()) || state.initiator().equals(actor)) return false;
        return jdbc.update(
                        "UPDATE community_direct_conversations SET"
                                + " status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        accept ? "ACTIVE" : "DECLINED",
                        id)
                == 1;
    }

    @Override
    public boolean markRead(UUID actor, UUID id, long sequence) {
        var view = conversation(actor, id);
        if (view.isEmpty()) return false;
        UUID left =
                jdbc.queryForObject(
                        "SELECT user_left FROM community_direct_conversations WHERE id=?",
                        UUID.class,
                        id);
        String column = left.equals(actor) ? "left_read_sequence" : "right_read_sequence";
        return jdbc.update(
                        "UPDATE community_direct_conversations SET "
                                + column
                                + "=GREATEST("
                                + column
                                + ",LEAST(?,COALESCE((SELECT max(sequence) FROM"
                                + " community_direct_messages WHERE conversation_id=?),0))) WHERE"
                                + " id=? AND (user_left=? OR user_right=?)",
                        sequence,
                        id,
                        id,
                        actor,
                        actor)
                == 1;
    }

    private DirectConversationView view(ResultSet rs, int row) throws SQLException {
        return new DirectConversationView(
                rs.getObject("id", UUID.class),
                rs.getObject("peer_id", UUID.class),
                rs.getString("peer_name"),
                rs.getObject("initiator_id", UUID.class),
                rs.getString("status"),
                rs.getString("last_message"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("unread_count"),
                rs.getLong("read_sequence"));
    }

    private DirectMessageView message(ResultSet rs, int row) throws SQLException {
        return new DirectMessageView(
                rs.getObject("id", UUID.class),
                rs.getLong("sequence"),
                rs.getObject("author_id", UUID.class),
                rs.getString("body"),
                rs.getTimestamp("created_at").toInstant());
    }

    private BusinessException missing() {
        return new BusinessException(
                "direct_chat_not_found", ErrorType.NOT_FOUND, "Không tìm thấy đoạn chat.");
    }

    private BusinessException conflict(String detail) {
        return new BusinessException("direct_chat_conflict", ErrorType.CONFLICT, detail);
    }
}
