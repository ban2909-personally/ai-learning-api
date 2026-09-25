package com.ailearning.platform.identity.adapter.in.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccount(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String role) {}
