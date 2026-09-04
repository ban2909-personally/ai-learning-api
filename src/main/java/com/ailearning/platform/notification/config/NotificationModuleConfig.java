package com.ailearning.platform.notification.config;

import com.ailearning.platform.notification.adapter.in.transaction.TransactionalNotificationUseCase;
import com.ailearning.platform.notification.api.usecase.NotificationUseCase;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.ailearning.platform.notification.application.port.out.NotificationStore;
import com.ailearning.platform.notification.application.service.impl.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class NotificationModuleConfig {
    @Bean
    NotificationUseCase notificationUseCase(
            NotificationStore notifications,
            NotificationRealtimeDelivery realtime,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        NotificationService core = new NotificationService(notifications, realtime, clock);
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
        return new TransactionalNotificationUseCase(core, readTransaction, writeTransaction);
    }
}
