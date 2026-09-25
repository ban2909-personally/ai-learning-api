package com.ailearning.platform.flashcard.application.service.impl;

import com.ailearning.platform.flashcard.api.usecase.FlashcardUseCase;
import com.ailearning.platform.flashcard.application.port.out.FlashcardStore;
import com.ailearning.platform.flashcard.domain.model.*;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.*;

import java.util.List;
import java.util.UUID;

public class FlashcardService implements FlashcardUseCase {
    private final FlashcardStore store;
    private final AccountAccess access;

    public FlashcardService(FlashcardStore store, AccountAccess access) {
        this.store = store;
        this.access = access;
    }

    private void authorize(UUID actor) {
        var roles = access.requireActive(actor).roles();
        if (roles.isEmpty() || roles.stream().allMatch("GUEST"::equals)) {
            throw new BusinessException(
                    "flashcards_forbidden",
                    ErrorType.FORBIDDEN,
                    "Đăng ký tài khoản học viên để sử dụng thẻ ghi nhớ.");
        }
    }

    public List<DeckSummary> list(UUID actor, String search, int page) {
        authorize(actor);
        return store.list(actor, search, Math.max(0, page));
    }

    public FlashcardDeck get(UUID actor, UUID deck) {
        authorize(actor);
        return store.find(actor, deck)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "deck_not_found",
                                        ErrorType.NOT_FOUND,
                                        "Không tìm thấy bộ thẻ."));
    }

    public FlashcardDeck save(
            UUID actor, UUID deck, String title, String description, List<Flashcard> cards) {
        authorize(actor);
        if (deck != null) get(actor, deck);
        if (title.isBlank()
                || title.length() > 180
                || description.length() > 1000
                || cards.isEmpty()
                || cards.size() > 200
                || cards.stream()
                        .anyMatch(
                                card ->
                                        card.front().isBlank()
                                                || card.back().isBlank()
                                                || card.front().length() > 2000
                                                || card.back().length() > 4000)) {
            throw new BusinessException(
                    "invalid_deck",
                    ErrorType.BAD_REQUEST,
                    "Bộ thẻ cần tiêu đề và từ 1 đến 200 thẻ có đủ hai mặt.");
        }
        return store.save(
                new FlashcardDeck(
                        deck == null ? UUID.randomUUID() : deck,
                        actor,
                        title.trim(),
                        description.trim(),
                        cards));
    }

    public void delete(UUID actor, UUID deck) {
        get(actor, deck);
        store.delete(actor, deck);
    }
}
