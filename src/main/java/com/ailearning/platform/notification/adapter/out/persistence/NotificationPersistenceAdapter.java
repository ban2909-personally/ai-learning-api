package com.ailearning.platform.notification.adapter.out.persistence;

import com.ailearning.platform.notification.application.port.out.NotificationStore;
import com.ailearning.platform.notification.domain.enums.NotificationType;
import com.ailearning.platform.notification.domain.model.Notification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationPersistenceAdapter implements NotificationStore {
    private static final String INSERT_SQL = """
            INSERT INTO user_notifications (
                id, user_id, type, title, body, target_path, created_at, read_at
            ) VALUES (
                :id, :userId, :type, :title, :body, :targetPath, :createdAt, :readAt
            )
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String FIND_PAGE_SQL = """
            SELECT id, user_id, type, title, body, target_path, created_at, read_at
            FROM user_notifications AS notification
            WHERE notification.user_id = :userId
              AND (
                    CAST(:beforeId AS UUID) IS NULL
                    OR (notification.created_at, notification.id) < (
                        SELECT cursor.created_at, cursor.id
                        FROM user_notifications AS cursor
                        WHERE cursor.id = CAST(:beforeId AS UUID)
                          AND cursor.user_id = :userId
                    )
              )
            ORDER BY notification.created_at DESC, notification.id DESC
            LIMIT :fetchSize
            """;

    private static final String COUNT_UNREAD_SQL = """
            SELECT COUNT(*)
            FROM user_notifications
            WHERE user_id = :userId
              AND read_at IS NULL
            """;

    private static final String MARK_READ_SQL = """
            UPDATE user_notifications
            SET read_at = COALESCE(read_at, :readAt)
            WHERE id = :notificationId
              AND user_id = :userId
            RETURNING id, user_id, type, title, body, target_path, created_at, read_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationPersistenceAdapter(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public boolean create(Notification notification) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", notification.id())
                .addValue("userId", notification.recipientId())
                .addValue("type", notification.type().name())
                .addValue("title", notification.title())
                .addValue("body", notification.body())
                .addValue("targetPath", notification.targetPath())
                .addValue("createdAt", Timestamp.from(notification.createdAt()))
                .addValue("readAt", timestamp(notification.readAt()));
        return jdbc.update(INSERT_SQL, parameters) == 1;
    }

    @Override
    public List<Notification> findByRecipient(UUID recipientId, UUID before, int fetchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", recipientId)
                .addValue("beforeId", before, Types.OTHER)
                .addValue("fetchSize", fetchSize);
        return jdbc.query(FIND_PAGE_SQL, parameters, this::mapNotification);
    }

    @Override
    public long countUnread(UUID recipientId) {
        Long count = jdbc.queryForObject(
                COUNT_UNREAD_SQL,
                new MapSqlParameterSource("userId", recipientId),
                Long.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Notification> markRead(UUID recipientId, UUID notificationId, Instant readAt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", recipientId)
                .addValue("notificationId", notificationId)
                .addValue("readAt", Timestamp.from(readAt));
        return jdbc.query(MARK_READ_SQL, parameters, this::mapNotification).stream().findFirst();
    }

    private Notification mapNotification(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp readAt = resultSet.getTimestamp("read_at");
        return new Notification(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                NotificationType.valueOf(resultSet.getString("type")),
                resultSet.getString("title"),
                resultSet.getString("body"),
                resultSet.getString("target_path"),
                resultSet.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant()
        );
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
