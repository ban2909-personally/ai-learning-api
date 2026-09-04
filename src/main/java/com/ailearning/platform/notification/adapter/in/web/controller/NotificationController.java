package com.ailearning.platform.notification.adapter.in.web.controller;

import com.ailearning.platform.notification.adapter.in.web.dto.response.NotificationPageResponse;
import com.ailearning.platform.notification.adapter.in.web.dto.response.NotificationResponse;
import com.ailearning.platform.notification.api.usecase.NotificationUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {
    private final NotificationUseCase notifications;

    public NotificationController(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    NotificationPageResponse findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        return NotificationPageResponse.from(
                notifications.findMine(UUID.fromString(jwt.getSubject()), before, limit)
        );
    }

    @PatchMapping("/{notificationId}/read")
    NotificationResponse markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId
    ) {
        return NotificationResponse.from(
                notifications.markRead(UUID.fromString(jwt.getSubject()), notificationId)
        );
    }
}
