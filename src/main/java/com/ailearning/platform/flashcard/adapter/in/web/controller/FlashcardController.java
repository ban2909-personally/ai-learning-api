package com.ailearning.platform.flashcard.adapter.in.web.controller;

import com.ailearning.platform.flashcard.adapter.in.web.dto.request.DeckRequest;
import com.ailearning.platform.flashcard.api.usecase.FlashcardUseCase;
import com.ailearning.platform.flashcard.domain.model.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/flashcards")
public class FlashcardController {
    private final FlashcardUseCase flashcards;

    public FlashcardController(FlashcardUseCase flashcards) {
        this.flashcards = flashcards;
    }

    @GetMapping
    List<DeckSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return flashcards.list(UUID.fromString(jwt.getSubject()), search, page);
    }

    @GetMapping("/{id}")
    FlashcardDeck get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return flashcards.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    FlashcardDeck create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeckRequest request) {
        return save(jwt, null, request);
    }

    @PutMapping("/{id}")
    FlashcardDeck save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody DeckRequest request) {
        return flashcards.save(
                UUID.fromString(jwt.getSubject()),
                id,
                request.title(),
                request.description(),
                request.cards().stream()
                        .map(card -> new Flashcard(card.front(), card.back()))
                        .toList());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        flashcards.delete(UUID.fromString(jwt.getSubject()), id);
    }
}
