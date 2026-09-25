package com.ailearning.platform.identity.adapter.in.web.controller;

import com.ailearning.platform.identity.adapter.in.web.dto.request.CreateAccount;
import com.ailearning.platform.identity.adapter.in.web.dto.request.UpdateAccount;
import com.ailearning.platform.identity.api.contract.AccountPage;
import com.ailearning.platform.identity.api.usecase.AccountManagementUseCase;
import com.ailearning.platform.identity.domain.model.ManagedAccount;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountManagementController {
    private final AccountManagementUseCase accounts;

    public AccountManagementController(AccountManagementUseCase accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    AccountPage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accounts.list(UUID.fromString(jwt.getSubject()), search, role, page, size);
    }

    @GetMapping("/summary")
    java.util.Map<String, Long> summary(@AuthenticationPrincipal Jwt jwt) {
        return accounts.statistics(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    ManagedAccount create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccount request) {
        return accounts.create(
                UUID.fromString(jwt.getSubject()),
                request.email(),
                request.displayName(),
                request.password(),
                request.role());
    }

    @PatchMapping("/{id}")
    void update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccount request) {
        accounts.update(UUID.fromString(jwt.getSubject()), id, request.role(), request.status());
    }
}
