package com.ailearning.platform.commerce.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseOrderRequest(
        @NotBlank
        @Size(max = 160)
        String courseSlug
) {
}
