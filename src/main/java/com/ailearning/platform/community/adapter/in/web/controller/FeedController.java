package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.adapter.in.web.dto.request.CommentRequest;
import com.ailearning.platform.community.adapter.in.web.dto.request.PostRequest;
import com.ailearning.platform.community.api.contract.CommentView;
import com.ailearning.platform.community.api.contract.CreatePostCommand;
import com.ailearning.platform.community.api.contract.FeedPage;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
public class FeedController {
    private final CommunityUseCase community;

    public FeedController(CommunityUseCase community) {
        this.community = community;
    }

    @GetMapping("/feed")
    FeedPage feed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam(defaultValue = "12") int size) {
        return community.feed(actor(jwt), spaceId, cursor, size);
    }

    @GetMapping("/posts/{id}")
    PostView post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.post(actor(jwt), id);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PostRequest request) {
        return community.createPost(
                actor(jwt),
                new CreatePostCommand(request.body(), request.spaceId(), request.sharedPostId()));
    }

    @PostMapping("/posts/{id}/likes")
    PostView like(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.like(actor(jwt), id, true);
    }

    @DeleteMapping("/posts/{id}/likes")
    PostView unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.like(actor(jwt), id, false);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        community.removePost(actor(jwt), id);
    }

    @GetMapping("/posts/{id}/comments")
    List<CommentView> comments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page) {
        return community.comments(actor(jwt), id, page);
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentView comment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request) {
        return community.comment(actor(jwt), id, request.parentId(), request.body());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        community.removeComment(actor(jwt), id);
    }

    private UUID actor(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
