package com.ailearning.platform.analytics.adapter.in.web.controller;

import com.ailearning.platform.analytics.adapter.in.web.dto.response.LearningAnalyticsResponse;
import com.ailearning.platform.analytics.api.usecase.LearningAnalyticsUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/me/learning-analytics")
public class LearningAnalyticsController {
    private final LearningAnalyticsUseCase analytics;

    public LearningAnalyticsController(LearningAnalyticsUseCase analytics) {
        this.analytics = analytics;
    }

    @GetMapping
    LearningAnalyticsResponse findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int courseLimit
    ) {
        return LearningAnalyticsResponse.from(
                analytics.findMine(UUID.fromString(jwt.getSubject()), courseLimit)
        );
    }
}
