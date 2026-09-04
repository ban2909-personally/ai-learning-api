package com.ailearning.platform.platform.web.error;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsOversizedUploadsToPayloadTooLargeProblem() {
        var request = new MockHttpServletRequest("PUT", "/api/v1/instructor/courses/java/lessons/1/media");

        var problem = handler.handleUploadSizeExceeded(
                new MaxUploadSizeExceededException(2_000_000_000L),
                request
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getProperties()).containsEntry("code", "payload_too_large");
        assertThat(problem.getInstance()).hasToString(request.getRequestURI());
    }

    @Test
    void mapsMethodConstraintViolationsToSafeBadRequestProblem() {
        var request = new MockHttpServletRequest("GET", "/api/v1/me/notifications");

        var problem = handler.handleConstraintViolation(
                new ConstraintViolationException(java.util.Set.of()),
                request
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Tham số yêu cầu không hợp lệ.");
        assertThat(problem.getProperties()).containsEntry("code", "validation_failed");
    }
}
