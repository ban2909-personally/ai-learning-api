package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.adapter.in.web.dto.request.InviteRequest;
import com.ailearning.platform.community.adapter.in.web.dto.request.RoleRequest;
import com.ailearning.platform.community.adapter.in.web.dto.request.SpaceRequest;
import com.ailearning.platform.community.api.contract.CreateSpaceCommand;
import com.ailearning.platform.community.api.contract.MemberView;
import com.ailearning.platform.community.api.contract.SpaceView;
import com.ailearning.platform.community.api.usecase.CommunityUseCase;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/community/spaces")
public class SpaceController {
    private final CommunityUseCase community;

    public SpaceController(CommunityUseCase community) {
        this.community = community;
    }

    @GetMapping
    List<SpaceView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return community.spaces(actor(jwt), search, page);
    }

    @GetMapping("/{id}")
    SpaceView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.space(actor(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SpaceView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SpaceRequest request) {
        return community.createSpace(
                actor(jwt),
                new CreateSpaceCommand(
                        request.name(),
                        request.description(),
                        request.kind(),
                        request.visibility()));
    }

    @GetMapping("/{id}/members")
    List<MemberView> members(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page) {
        return community.members(actor(jwt), id, page);
    }

    @PostMapping("/{id}/join")
    SpaceView join(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.join(actor(jwt), id);
    }

    @PostMapping("/{id}/invite")
    SpaceView invite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody InviteRequest request) {
        return community.invite(actor(jwt), id, request.email());
    }

    @PostMapping("/{id}/members/{userId}/approve")
    SpaceView approve(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        return community.decide(actor(jwt), id, userId, true);
    }

    @PostMapping("/{id}/members/{userId}/reject")
    SpaceView reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        return community.decide(actor(jwt), id, userId, false);
    }

    @DeleteMapping("/{id}/members/{userId}")
    SpaceView remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        return community.removeMember(actor(jwt), id, userId);
    }

    @PatchMapping("/{id}/members/{userId}/role")
    SpaceView changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleRequest request) {
        return community.changeRole(actor(jwt), id, userId, request.role());
    }

    @DeleteMapping("/{id}/membership")
    SpaceView leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.leave(actor(jwt), id);
    }

    private UUID actor(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
