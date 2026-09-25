package com.ailearning.platform.flashcard.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DeckSummary(
        UUID id, String title, String description, int cardCount, Instant updatedAt) {}
