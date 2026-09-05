package com.ailearning.platform.commerce.adapter.in.web.controller;

import com.ailearning.platform.commerce.adapter.in.web.dto.request.CreateCourseOrderRequest;
import com.ailearning.platform.commerce.adapter.in.web.dto.response.CourseOrderResponse;
import com.ailearning.platform.commerce.api.usecase.CourseOrderUseCase;
import com.ailearning.platform.commerce.application.command.CreateCourseOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/me/orders")
public class CourseOrderController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final CourseOrderUseCase orders;

    public CourseOrderController(CourseOrderUseCase orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<CourseOrderResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) UUID idempotencyKey,
            @Valid @RequestBody CreateCourseOrderRequest request
    ) {
        var result = orders.create(new CreateCourseOrderCommand(
                UUID.fromString(jwt.getSubject()),
                request.courseSlug(),
                idempotencyKey
        ));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CourseOrderResponse.from(result.order()));
    }

    @GetMapping
    List<CourseOrderResponse> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return orders.findRecent(UUID.fromString(jwt.getSubject()), limit).stream()
                .map(CourseOrderResponse::from)
                .toList();
    }
}
