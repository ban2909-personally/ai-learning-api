package com.ailearning.platform.catalog.adapter.in.web.controller;

import com.ailearning.platform.catalog.adapter.in.web.dto.request.CourseRequest;
import com.ailearning.platform.catalog.adapter.in.web.dto.request.LessonRequest;
import com.ailearning.platform.catalog.adapter.in.web.dto.request.StatusRequest;
import com.ailearning.platform.catalog.api.contract.*;
import com.ailearning.platform.catalog.api.usecase.CourseAuthoringUseCase;
import com.ailearning.platform.catalog.domain.model.ManagedCourse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instructor/courses")
@PreAuthorize("hasAnyRole('ADMIN','LEADER','LECTURE','INSTRUCTOR')")
public class CourseAuthoringController {
    private final CourseAuthoringUseCase courses;

    public CourseAuthoringController(CourseAuthoringUseCase courses) {
        this.courses = courses;
    }

    @GetMapping("/summary")
    java.util.Map<String, Long> summary(@AuthenticationPrincipal Jwt jwt) {
        return courses.statistics(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    List<ManagedCourse> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
        return courses.list(UUID.fromString(jwt.getSubject()), page);
    }

    @GetMapping("/{id}")
    CourseWorkspace get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return courses.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    ManagedCourse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CourseRequest request) {
        return courses.create(
                UUID.fromString(jwt.getSubject()),
                new CreateCourseCommand(
                        request.categoryId(),
                        request.slug(),
                        request.title(),
                        request.shortDescription(),
                        request.description(),
                        request.level(),
                        request.price()));
    }

    @PostMapping("/{id}/lessons")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    void addLesson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody LessonRequest request) {
        courses.addLesson(
                UUID.fromString(jwt.getSubject()),
                id,
                new CreateLessonCommand(
                        request.sectionTitle(),
                        request.title(),
                        request.contentUrl(),
                        request.durationSeconds(),
                        request.preview()));
    }

    @PatchMapping("/{id}/status")
    void transition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody StatusRequest request) {
        courses.transition(UUID.fromString(jwt.getSubject()), id, request.status());
    }
}
