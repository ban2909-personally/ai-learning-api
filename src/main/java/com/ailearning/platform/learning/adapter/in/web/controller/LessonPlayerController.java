package com.ailearning.platform.learning.adapter.in.web.controller;

import com.ailearning.platform.learning.adapter.in.web.dto.response.LessonPlayerResponse;
import com.ailearning.platform.learning.api.usecase.LessonAccessUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/courses")
public class LessonPlayerController {
    private final LessonAccessUseCase lessons;
    public LessonPlayerController(LessonAccessUseCase lessons) { this.lessons = lessons; }
    @GetMapping("/{courseSlug}/lessons/{lessonId}")
    LessonPlayerResponse open(@AuthenticationPrincipal Jwt jwt, @PathVariable String courseSlug,
            @PathVariable UUID lessonId) {
        return LessonPlayerResponse.from(lessons.openLesson(UUID.fromString(jwt.getSubject()), courseSlug, lessonId));
    }
}
