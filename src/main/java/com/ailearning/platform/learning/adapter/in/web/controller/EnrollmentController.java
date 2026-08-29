package com.ailearning.platform.learning.adapter.in.web.controller;

import com.ailearning.platform.learning.adapter.in.web.dto.response.EnrollmentResponse;
import com.ailearning.platform.learning.api.usecase.EnrollmentUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {
    private final EnrollmentUseCase enrollments;
    public EnrollmentController(EnrollmentUseCase enrollments) { this.enrollments = enrollments; }
    @PostMapping("/courses/{slug}/enrollments")
    EnrollmentResponse enroll(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug) {
        return EnrollmentResponse.from(enrollments.enroll(UUID.fromString(jwt.getSubject()), slug));
    }
    @GetMapping("/me/enrollments")
    List<EnrollmentResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return enrollments.findMine(UUID.fromString(jwt.getSubject())).stream().map(EnrollmentResponse::from).toList();
    }
}
