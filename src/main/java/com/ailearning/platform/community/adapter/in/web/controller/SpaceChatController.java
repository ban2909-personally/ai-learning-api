package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.adapter.in.web.dto.request.SpaceMessageRequest;
import com.ailearning.platform.community.api.contract.SpaceChatPage;
import com.ailearning.platform.community.api.contract.SpaceMessageView;
import com.ailearning.platform.community.api.usecase.SpaceChatUseCase;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/spaces/{spaceId}/chat")
public class SpaceChatController {
    private final SpaceChatUseCase chat;

    public SpaceChatController(SpaceChatUseCase chat) {
        this.chat = chat;
    }

    @GetMapping
    SpaceChatPage messages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before) {
        return chat.messages(UUID.fromString(jwt.getSubject()), spaceId, after, before);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SpaceMessageView send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @Valid @RequestBody SpaceMessageRequest request) {
        return chat.send(
                UUID.fromString(jwt.getSubject()), spaceId, request.clientId(), request.body());
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @PathVariable UUID messageId) {
        chat.remove(UUID.fromString(jwt.getSubject()), spaceId, messageId);
    }
}
