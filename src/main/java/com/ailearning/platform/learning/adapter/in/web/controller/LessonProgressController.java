package com.ailearning.platform.learning.adapter.in.web.controller;

import com.ailearning.platform.learning.adapter.in.web.dto.request.SaveLessonProgressRequest;
import com.ailearning.platform.learning.adapter.in.web.dto.response.LessonProgressResponse;
import com.ailearning.platform.learning.api.usecase.LessonProgressUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/courses/{courseSlug}/lessons/{lessonId}/progress")
public class LessonProgressController {
    private final LessonProgressUseCase progress;
    public LessonProgressController(LessonProgressUseCase progress) { this.progress = progress; }
    @GetMapping LessonProgressResponse find(@AuthenticationPrincipal Jwt jwt, @PathVariable String courseSlug,
            @PathVariable UUID lessonId) {
        return LessonProgressResponse.from(progress.find(UUID.fromString(jwt.getSubject()), courseSlug, lessonId));
    }
    @PutMapping LessonProgressResponse save(@AuthenticationPrincipal Jwt jwt, @PathVariable String courseSlug,
            @PathVariable UUID lessonId, @Valid @RequestBody SaveLessonProgressRequest request) {
        return LessonProgressResponse.from(progress.save(UUID.fromString(jwt.getSubject()), courseSlug, lessonId,
                request.positionSeconds(), request.completed()));
    }
}
