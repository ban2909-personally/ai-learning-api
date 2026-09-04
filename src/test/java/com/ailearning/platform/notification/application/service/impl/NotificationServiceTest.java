package com.ailearning.platform.notification.application.service.impl;

import com.ailearning.platform.notification.api.contract.LessonCompletedNotificationCommand;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.ailearning.platform.notification.application.port.out.NotificationStore;
import com.ailearning.platform.notification.domain.model.Notification;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T08:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final NotificationStore store = mock(NotificationStore.class);
    private final NotificationRealtimeDelivery realtime = mock(NotificationRealtimeDelivery.class);
    private final NotificationService service = new NotificationService(
            store,
            realtime,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void projectsAndPublishesANewLessonCompletion() {
        UUID eventId = UUID.randomUUID();
        var command = new LessonCompletedNotificationCommand(eventId, USER_ID, NOW.minusSeconds(5));
        when(store.create(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        var projected = service.projectLessonCompleted(command);

        assertThat(projected).isPresent().get()
                .satisfies(notification -> {
                    assertThat(notification.id()).isEqualTo(eventId);
                    assertThat(notification.title()).isEqualTo("Hoàn thành bài học");
                    assertThat(notification.targetPath()).isEqualTo("/my-learning");
                    verify(realtime).publish(notification);
                });
    }

    @Test
    void ignoresADuplicateEventWithoutAnotherRealtimeDelivery() {
        var command = new LessonCompletedNotificationCommand(UUID.randomUUID(), USER_ID, NOW);
        when(store.create(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        assertThat(service.projectLessonCompleted(command)).isEmpty();

        verify(realtime, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsABoundedPageAndCursorForTheLastVisibleItem() {
        Notification newest = notification(NOW, null);
        Notification middle = notification(NOW.minusSeconds(1), null);
        Notification oldest = notification(NOW.minusSeconds(2), null);
        when(store.findByRecipient(USER_ID, null, 3)).thenReturn(List.of(newest, middle, oldest));
        when(store.countUnread(USER_ID)).thenReturn(3L);

        var page = service.findMine(USER_ID, null, 2);

        assertThat(page.content()).extracting(value -> value.id())
                .containsExactly(newest.id(), middle.id());
        assertThat(page.nextCursor()).isEqualTo(middle.id());
        assertThat(page.unreadCount()).isEqualTo(3);
    }

    @Test
    void rejectsAnUnboundedPageSize() {
        assertThatThrownBy(() -> service.findMine(USER_ID, null, NotificationService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("invalid_notification_page_size");
    }

    @Test
    void marksOnlyAnOwnedNotificationRead() {
        UUID notificationId = UUID.randomUUID();
        Notification read = new Notification(
                notificationId,
                USER_ID,
                com.ailearning.platform.notification.domain.enums.NotificationType.LESSON_COMPLETED,
                "Hoàn thành bài học",
                "Nội dung",
                "/my-learning",
                NOW.minusSeconds(10),
                NOW
        );
        when(store.markRead(USER_ID, notificationId, NOW)).thenReturn(Optional.of(read));

        assertThat(service.markRead(USER_ID, notificationId).readAt()).isEqualTo(NOW);
    }

    @Test
    void hidesWhetherAnotherUsersNotificationExists() {
        UUID notificationId = UUID.randomUUID();
        when(store.markRead(USER_ID, notificationId, NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(USER_ID, notificationId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("notification_not_found");
    }

    private Notification notification(Instant createdAt, Instant readAt) {
        return new Notification(
                UUID.randomUUID(),
                USER_ID,
                com.ailearning.platform.notification.domain.enums.NotificationType.LESSON_COMPLETED,
                "Hoàn thành bài học",
                "Nội dung",
                "/my-learning",
                createdAt,
                readAt
        );
    }
}
