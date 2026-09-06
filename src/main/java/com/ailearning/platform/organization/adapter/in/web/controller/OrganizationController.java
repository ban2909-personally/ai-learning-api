package com.ailearning.platform.organization.adapter.in.web.controller;

import com.ailearning.platform.organization.adapter.in.web.dto.request.CreateOrganizationRequest;
import com.ailearning.platform.organization.adapter.in.web.dto.response.OrganizationMemberResponse;
import com.ailearning.platform.organization.adapter.in.web.dto.response.OrganizationResponse;
import com.ailearning.platform.organization.api.usecase.OrganizationUseCase;
import com.ailearning.platform.organization.application.command.CreateOrganizationCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
public class OrganizationController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final OrganizationUseCase organizations;

    public OrganizationController(OrganizationUseCase organizations) {
        this.organizations = organizations;
    }

    @PostMapping("/api/v1/me/organizations")
    ResponseEntity<OrganizationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) UUID idempotencyKey,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        var result = organizations.create(new CreateOrganizationCommand(
                UUID.fromString(jwt.getSubject()),
                request.name(),
                request.slug(),
                idempotencyKey
        ));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(OrganizationResponse.from(result.organization()));
    }

    @GetMapping("/api/v1/me/organizations")
    List<OrganizationResponse> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return organizations.findMine(UUID.fromString(jwt.getSubject()), limit).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/organizations/{organizationId}/members")
    List<OrganizationMemberResponse> findMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return organizations.findMembers(UUID.fromString(jwt.getSubject()), organizationId, limit).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
    }
}
