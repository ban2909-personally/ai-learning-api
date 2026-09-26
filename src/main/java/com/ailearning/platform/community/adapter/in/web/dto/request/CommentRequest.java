package com.ailearning.platform.community.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommentRequest(@NotBlank @Size(max = 2000) String body, UUID parentId) {}
