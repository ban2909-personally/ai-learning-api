package com.ailearning.platform.notification.adapter.out.persistence;

import com.ailearning.platform.notification.domain.enums.NotificationType;
import com.ailearning.platform.notification.domain.model.Notification;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class NotificationPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-04T08:00:00Z");
    private static final UUID FIRST_USER = UUID.randomUUID();
    private static final UUID SECOND_USER = UUID.randomUUID();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_notification_test")
            .withUsername("test")
            .withPassword("test");

    private static NotificationPersistenceAdapter notifications;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        notifications = new NotificationPersistenceAdapter(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM user_notifications");
    }

    @Test
    void createsOnlyOneProjectionForTheSameEventId() {
        UUID eventId = UUID.randomUUID();
        Notification original = notification(eventId, FIRST_USER, NOW);
        Notification duplicate = notification(eventId, SECOND_USER, NOW.plusSeconds(1));

        assertThat(notifications.create(original)).isTrue();
        assertThat(notifications.create(duplicate)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications", Integer.class))
                .isOne();
    }

    @Test
    void pagesByStableCursorAndNeverLeaksAnotherUsersRows() {
        Notification newest = notification(UUID.randomUUID(), FIRST_USER, NOW);
        Notification middle = notification(UUID.randomUUID(), FIRST_USER, NOW.minusSeconds(1));
        Notification oldest = notification(UUID.randomUUID(), FIRST_USER, NOW.minusSeconds(2));
        notifications.create(oldest);
        notifications.create(newest);
        notifications.create(middle);
        notifications.create(notification(UUID.randomUUID(), SECOND_USER, NOW.plusSeconds(1)));

        var firstPage = notifications.findByRecipient(FIRST_USER, null, 2);
        var secondPage = notifications.findByRecipient(FIRST_USER, middle.id(), 2);

        assertThat(firstPage).extracting(Notification::id)
                .containsExactly(newest.id(), middle.id());
        assertThat(secondPage).extracting(Notification::id)
                .containsExactly(oldest.id());
        assertThat(notifications.findByRecipient(SECOND_USER, middle.id(), 2)).isEmpty();
    }

    @Test
    void countsUnreadAndMarksOnlyTheRecipientsNotificationOnce() {
        Notification notification = notification(UUID.randomUUID(), FIRST_USER, NOW.minusSeconds(5));
        notifications.create(notification);

        assertThat(notifications.countUnread(FIRST_USER)).isOne();
        assertThat(notifications.markRead(SECOND_USER, notification.id(), NOW)).isEmpty();

        var firstRead = notifications.markRead(FIRST_USER, notification.id(), NOW).orElseThrow();
        var repeatedRead = notifications.markRead(
                FIRST_USER,
                notification.id(),
                NOW.plusSeconds(10)
        ).orElseThrow();

        assertThat(firstRead.readAt()).isEqualTo(NOW);
        assertThat(repeatedRead.readAt()).isEqualTo(NOW);
        assertThat(notifications.countUnread(FIRST_USER)).isZero();
    }

    private Notification notification(UUID id, UUID userId, Instant createdAt) {
        return new Notification(
                id,
                userId,
                NotificationType.LESSON_COMPLETED,
                "Hoàn thành bài học",
                "Bạn đã hoàn thành một bài học.",
                "/my-learning",
                createdAt,
                null
        );
    }
}
