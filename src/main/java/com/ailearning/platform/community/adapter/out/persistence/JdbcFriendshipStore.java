package com.ailearning.platform.community.adapter.out.persistence;

import com.ailearning.platform.community.api.contract.FriendConnection;
import com.ailearning.platform.community.api.contract.FriendPage;
import com.ailearning.platform.community.api.contract.FriendshipSummary;
import com.ailearning.platform.community.application.port.out.FriendshipStore;
import com.ailearning.platform.community.domain.model.Friendship;
import com.ailearning.platform.community.domain.policy.FriendshipPolicy;
import com.ailearning.platform.identity.api.contract.PublicProfile;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcFriendshipStore implements FriendshipStore {
    private final JdbcTemplate jdbc;
    private final FriendshipPolicy policy = new FriendshipPolicy();

    public JdbcFriendshipStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private UUID left(UUID actor, UUID peer) {
        return actor.toString().compareTo(peer.toString()) < 0 ? actor : peer;
    }

    private UUID right(UUID actor, UUID peer) {
        return left(actor, peer).equals(actor) ? peer : actor;
    }

    private Optional<Friendship> state(UUID actor, UUID peer, boolean lock) {
        return jdbc
                .query(
                        "SELECT initiator_id,status FROM community_friendships WHERE user_left=?"
                                + " AND user_right=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, row) ->
                                new Friendship(
                                        rs.getObject("initiator_id", UUID.class),
                                        rs.getString("status")),
                        left(actor, peer),
                        right(actor, peer))
                .stream()
                .findFirst();
    }

    // Locks account pairs in the same order for every mutation, including absent friendship rows.
    private void lockPair(UUID actor, UUID peer) {
        var users =
                jdbc.query(
                        "SELECT id,status FROM users WHERE id IN (?,?) ORDER BY id FOR UPDATE",
                        (rs, row) -> rs.getString("status"),
                        left(actor, peer),
                        right(actor, peer));
        if (users.size() != 2 || users.stream().anyMatch(status -> !"ACTIVE".equals(status)))
            throw missing();
    }

    @Override
    public FriendshipSummary summary(UUID viewer, UUID person) {
        long count =
                jdbc.queryForObject(
                        """
SELECT count(*) FROM community_friendships f JOIN users u
  ON u.id=CASE WHEN f.user_left=? THEN f.user_right ELSE f.user_left END
WHERE (f.user_left=? OR f.user_right=?) AND f.status='ACCEPTED' AND u.status='ACTIVE'
""",
                        Long.class,
                        person,
                        person,
                        person);
        long mutual =
                viewer == null || person.equals(viewer)
                        ? 0
                        : jdbc.queryForObject(
                                """
WITH their_friends AS (
  SELECT CASE WHEN user_left=? THEN user_right ELSE user_left END AS id
  FROM community_friendships WHERE (user_left=? OR user_right=?) AND status='ACCEPTED'
), my_friends AS (
  SELECT CASE WHEN user_left=? THEN user_right ELSE user_left END AS id
  FROM community_friendships WHERE (user_left=? OR user_right=?) AND status='ACCEPTED'
) SELECT count(*) FROM their_friends t JOIN my_friends m ON t.id=m.id
  JOIN users u ON u.id=t.id WHERE u.status='ACTIVE'
""",
                                Long.class,
                                person,
                                person,
                                person,
                                viewer,
                                viewer,
                                viewer);
        Friendship friendship =
                viewer == null || person.equals(viewer)
                        ? null
                        : state(viewer, person, false).orElse(null);
        return new FriendshipSummary(
                policy.relationship(viewer, person, friendship), count, mutual);
    }

    @Override
    public FriendPage connections(UUID actor, String filter, int page) {
        String condition =
                switch (filter) {
                    case "incoming" -> "f.status='PENDING' AND f.initiator_id<>?";
                    case "outgoing" -> "f.status='PENDING' AND f.initiator_id=?";
                    default -> "f.status='ACCEPTED' AND CAST(? AS uuid) IS NOT NULL";
                };
        var rows =
                jdbc.query(
                        """
                        SELECT u.id,u.display_name,f.initiator_id,f.status,f.updated_at
                        FROM community_friendships f JOIN users u
                          ON u.id=CASE WHEN f.user_left=? THEN f.user_right ELSE f.user_left END
                        WHERE (f.user_left=? OR f.user_right=?) AND u.status='ACTIVE' AND
                        """
                                + condition
                                + " ORDER BY f.updated_at DESC,u.id LIMIT 21 OFFSET ?",
                        (rs, row) ->
                                new FriendConnection(
                                        new PublicProfile(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("display_name")),
                                        policy.relationship(
                                                actor,
                                                rs.getObject("id", UUID.class),
                                                new Friendship(
                                                        rs.getObject("initiator_id", UUID.class),
                                                        rs.getString("status"))),
                                        rs.getTimestamp("updated_at").toInstant()),
                        actor,
                        actor,
                        actor,
                        actor,
                        (long) page * 20);
        return new FriendPage(
                java.util.List.copyOf(rows.subList(0, Math.min(rows.size(), 20))),
                rows.size() > 20 ? page + 1 : null);
    }

    @Override
    @Transactional
    public void request(UUID actor, UUID peer) {
        lockPair(actor, peer);
        jdbc.update(
                "INSERT INTO community_friendships(user_left,user_right,initiator_id,status) VALUES"
                        + " (?,?,?,'PENDING') ON CONFLICT(user_left,user_right) DO NOTHING",
                left(actor, peer),
                right(actor, peer),
                actor);
        policy.requireRequest(actor, state(actor, peer, true).orElseThrow(this::missing));
    }

    @Override
    @Transactional
    public void accept(UUID actor, UUID peer) {
        lockPair(actor, peer);
        policy.requireAccept(actor, state(actor, peer, true).orElseThrow(this::missing));
        jdbc.update(
                "UPDATE community_friendships SET status='ACCEPTED',updated_at=CURRENT_TIMESTAMP"
                        + " WHERE user_left=? AND user_right=?",
                left(actor, peer),
                right(actor, peer));
    }

    @Override
    @Transactional
    public void remove(UUID actor, UUID peer) {
        lockPair(actor, peer);
        jdbc.update(
                "DELETE FROM community_friendships WHERE user_left=? AND user_right=?",
                left(actor, peer),
                right(actor, peer));
    }

    private BusinessException missing() {
        return new BusinessException(
                "friendship_not_found",
                ErrorType.NOT_FOUND,
                "Không tìm thấy tài khoản hoặc lời mời kết bạn.");
    }
}
