package com.ailearning.platform.flashcard.api.usecase;

import com.ailearning.platform.flashcard.domain.model.*;

import java.util.List;
import java.util.UUID;

public interface FlashcardUseCase {
    List<DeckSummary> list(UUID actor, String search, int page);

    FlashcardDeck get(UUID actor, UUID deck);

    FlashcardDeck save(
            UUID actor, UUID deck, String title, String description, List<Flashcard> cards);

    void delete(UUID actor, UUID deck);
}
