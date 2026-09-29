package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.api.contract.DirectConversationView;
import com.ailearning.platform.community.api.contract.DirectInboxPage;
import com.ailearning.platform.community.api.contract.DirectMessagePage;
import com.ailearning.platform.community.api.contract.DirectMessageView;
import com.ailearning.platform.community.api.usecase.DirectChatUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/v1/community/direct")
public class DirectChatController {
    private final DirectChatUseCase chat;

    public DirectChatController(DirectChatUseCase chat) {
        this.chat = chat;
    }

    public record StartRequest(
            @NotBlank @Size(max = 254) String email,
            @NotNull UUID clientId,
            @NotBlank @Size(max = 2000) String body) {}

    public record MessageRequest(@NotNull UUID clientId, @NotBlank @Size(max = 2000) String body) {}

    public record DecisionRequest(boolean accept) {}

    public record ReadRequest(@Min(0) long sequence) {}

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/conversations")
    DirectInboxPage inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page) {
        return chat.inbox(actor(jwt), filter, page);
    }

    @GetMapping("/peers/{peer}")
    ResponseEntity<DirectConversationView> findWithPeer(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID peer) {
        return chat.findWithPeer(actor(jwt), peer)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/peers/{peer}")
    @ResponseStatus(HttpStatus.CREATED)
    DirectConversationView startWithPeer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID peer,
            @Valid @RequestBody MessageRequest body) {
        return chat.startWithPeer(actor(jwt), peer, body.clientId(), body.body());
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    DirectConversationView start(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest body) {
        return chat.start(actor(jwt), body.email(), body.clientId(), body.body());
    }

    @GetMapping("/conversations/{id}/messages")
    DirectMessagePage messages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before) {
        return chat.messages(actor(jwt), id, after, before);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    DirectMessageView send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody MessageRequest body) {
        return chat.send(actor(jwt), id, body.clientId(), body.body());
    }

    @PostMapping("/conversations/{id}/decision")
    DirectConversationView decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest body) {
        return chat.decide(actor(jwt), id, body.accept());
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReadRequest body) {
        chat.markRead(actor(jwt), id, body.sequence());
    }
}
