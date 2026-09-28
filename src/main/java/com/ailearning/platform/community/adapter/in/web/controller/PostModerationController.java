package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/spaces/{spaceId}/posts")
public class PostModerationController {
    private final CommunityUseCase community;

    public PostModerationController(CommunityUseCase community) {
        this.community = community;
    }

    @GetMapping("/pending")
    List<PostView> pending(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @RequestParam(defaultValue = "0") int page) {
        return community.pendingPosts(UUID.fromString(jwt.getSubject()), spaceId, page);
    }

    @PostMapping("/{postId}/approve")
    PostView approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @PathVariable UUID postId) {
        return community.reviewPost(UUID.fromString(jwt.getSubject()), spaceId, postId, true);
    }

    @PostMapping("/{postId}/reject")
    PostView reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @PathVariable UUID postId) {
        return community.reviewPost(UUID.fromString(jwt.getSubject()), spaceId, postId, false);
    }
}
