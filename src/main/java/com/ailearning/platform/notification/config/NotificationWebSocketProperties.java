package com.ailearning.platform.notification.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.notifications.websocket")
public record NotificationWebSocketProperties(
        @NotNull @DataSizeUnit(DataUnit.KILOBYTES) DataSize messageSize,
        @NotNull Duration sendTimeLimit,
        @NotNull @DataSizeUnit(DataUnit.KILOBYTES) DataSize sendBufferSize,
        @NotNull Duration firstMessageTimeout,
        @NotNull Duration heartbeat
) {
    @AssertTrue(message = "notification WebSocket limits must be positive and fit integer transport limits")
    public boolean hasSafeLimits() {
        return isSafeSize(messageSize)
                && isSafeSize(sendBufferSize)
                && isPositive(sendTimeLimit)
                && isPositive(firstMessageTimeout)
                && isPositive(heartbeat)
                && sendTimeLimit.toMillis() <= Integer.MAX_VALUE
                && firstMessageTimeout.toMillis() <= Integer.MAX_VALUE;
    }

    public int messageSizeBytes() {
        return Math.toIntExact(messageSize.toBytes());
    }

    public int sendBufferSizeBytes() {
        return Math.toIntExact(sendBufferSize.toBytes());
    }

    private boolean isSafeSize(DataSize size) {
        return size != null && size.toBytes() > 0 && size.toBytes() <= Integer.MAX_VALUE;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
