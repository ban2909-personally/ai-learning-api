package com.ailearning.platform.flashcard.application.port.out;

import com.ailearning.platform.flashcard.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashcardStore {
    List<DeckSummary> list(UUID owner, String search, int page);

    Optional<FlashcardDeck> find(UUID owner, UUID deck);

    FlashcardDeck save(FlashcardDeck deck);

    void delete(UUID owner, UUID deck);
}
