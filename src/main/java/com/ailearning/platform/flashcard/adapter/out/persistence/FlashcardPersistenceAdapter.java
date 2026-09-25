package com.ailearning.platform.flashcard.adapter.out.persistence;

import com.ailearning.platform.flashcard.application.port.out.FlashcardStore;
import com.ailearning.platform.flashcard.domain.model.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FlashcardPersistenceAdapter implements FlashcardStore {
    private final JdbcTemplate jdbc;

    public FlashcardPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeckSummary> list(UUID owner, String search, int page) {
        return jdbc.query(
                """
                SELECT d.*,(SELECT count(*) FROM flashcards c WHERE c.deck_id=d.id) AS card_count
                FROM flashcard_decks d WHERE owner_id=? AND lower(title) LIKE ?
                ORDER BY updated_at DESC,id LIMIT 30 OFFSET ?
                """,
                (rs, row) ->
                        new DeckSummary(
                                rs.getObject("id", UUID.class),
                                rs.getString("title"),
                                rs.getString("description"),
                                rs.getInt("card_count"),
                                rs.getTimestamp("updated_at").toInstant()),
                owner,
                "%" + search.toLowerCase(java.util.Locale.ROOT) + "%",
                (long) page * 30);
    }

    @Transactional(readOnly = true)
    public Optional<FlashcardDeck> find(UUID owner, UUID deck) {
        return jdbc
                .query(
                        "SELECT * FROM flashcard_decks WHERE id=? AND owner_id=?",
                        (rs, row) ->
                                new FlashcardDeck(
                                        deck,
                                        owner,
                                        rs.getString("title"),
                                        rs.getString("description"),
                                        jdbc.query(
                                                "SELECT front,back FROM flashcards WHERE deck_id=?"
                                                        + " ORDER BY display_order",
                                                (card, index) ->
                                                        new Flashcard(
                                                                card.getString("front"),
                                                                card.getString("back")),
                                                deck)),
                        deck,
                        owner)
                .stream()
                .findFirst();
    }

    @Transactional
    public FlashcardDeck save(FlashcardDeck deck) {
        int affected =
                jdbc.update(
                        """
INSERT INTO flashcard_decks(id,owner_id,title,description) VALUES (?,?,?,?)
ON CONFLICT(id) DO UPDATE SET title=EXCLUDED.title,description=EXCLUDED.description,
updated_at=CURRENT_TIMESTAMP WHERE flashcard_decks.owner_id=EXCLUDED.owner_id
""",
                        deck.id(),
                        deck.ownerId(),
                        deck.title(),
                        deck.description());
        if (affected != 1) {
            throw new com.ailearning.platform.sharedkernel.error.BusinessException(
                    "flashcard_deck_not_found",
                    com.ailearning.platform.sharedkernel.error.ErrorType.NOT_FOUND,
                    "Không tìm thấy bộ thẻ.");
        }
        jdbc.update(
                "DELETE FROM flashcards WHERE deck_id=? AND EXISTS(SELECT 1 FROM flashcard_decks"
                        + " WHERE id=? AND owner_id=?)",
                deck.id(),
                deck.id(),
                deck.ownerId());
        for (int index = 0; index < deck.cards().size(); index++) {
            var card = deck.cards().get(index);
            jdbc.update(
                    "INSERT INTO flashcards(id,deck_id,front,back,display_order) VALUES"
                            + " (?,?,?,?,?)",
                    UUID.randomUUID(),
                    deck.id(),
                    card.front().trim(),
                    card.back().trim(),
                    index);
        }
        return deck;
    }

    @Transactional
    public void delete(UUID owner, UUID deck) {
        jdbc.update("DELETE FROM flashcard_decks WHERE id=? AND owner_id=?", deck, owner);
    }
}
