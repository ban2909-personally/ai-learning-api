package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.PollUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/posts/{postId}/poll")
public class PollController {
    private final PollUseCase polls;

    public PollController(PollUseCase polls) {
        this.polls = polls;
    }

    public record VoteRequest(@NotNull UUID optionId) {}

    @PostMapping("/votes")
    PostView vote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody VoteRequest body) {
        return polls.vote(UUID.fromString(jwt.getSubject()), postId, body.optionId());
    }

    @DeleteMapping("/votes")
    PostView retract(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return polls.vote(UUID.fromString(jwt.getSubject()), postId, null);
    }

    @PostMapping("/close")
    PostView close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postId) {
        return polls.close(UUID.fromString(jwt.getSubject()), postId);
    }
}
