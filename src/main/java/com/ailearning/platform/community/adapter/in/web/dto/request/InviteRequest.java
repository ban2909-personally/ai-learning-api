package com.ailearning.platform.community.adapter.in.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteRequest(@NotBlank @Email @Size(max = 254) String email) {}
