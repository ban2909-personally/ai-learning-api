package com.ailearning.platform.notification.adapter.in.transaction;

import com.ailearning.platform.notification.api.contract.LessonCompletedNotificationCommand;
import com.ailearning.platform.notification.api.contract.NotificationPageView;
import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.api.usecase.NotificationUseCase;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

public class TransactionalNotificationUseCase implements NotificationUseCase {
    private final NotificationUseCase delegate;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public TransactionalNotificationUseCase(
            NotificationUseCase delegate,
            TransactionTemplate readTransaction,
            TransactionTemplate writeTransaction
    ) {
        this.delegate = delegate;
        this.readTransaction = readTransaction;
        this.writeTransaction = writeTransaction;
    }

    @Override
    public Optional<NotificationView> projectLessonCompleted(LessonCompletedNotificationCommand command) {
        return writeTransaction.execute(status -> delegate.projectLessonCompleted(command));
    }

    @Override
    public NotificationPageView findMine(UUID userId, UUID before, int limit) {
        return readTransaction.execute(status -> delegate.findMine(userId, before, limit));
    }

    @Override
    public NotificationView markRead(UUID userId, UUID notificationId) {
        return writeTransaction.execute(status -> delegate.markRead(userId, notificationId));
    }
}
