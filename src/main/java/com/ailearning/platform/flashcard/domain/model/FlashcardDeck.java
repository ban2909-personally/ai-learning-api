package com.ailearning.platform.flashcard.domain.model;

import java.util.List;
import java.util.UUID;

public record FlashcardDeck(
        UUID id, UUID ownerId, String title, String description, List<Flashcard> cards) {
    public FlashcardDeck {
        cards = List.copyOf(cards);
    }
}
