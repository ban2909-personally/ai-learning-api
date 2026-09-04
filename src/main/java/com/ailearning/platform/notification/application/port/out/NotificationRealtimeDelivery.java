package com.ailearning.platform.notification.application.port.out;

import com.ailearning.platform.notification.api.contract.NotificationView;

public interface NotificationRealtimeDelivery {
    void publish(NotificationView notification);
}
