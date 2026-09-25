CREATE TABLE flashcard_decks (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), title VARCHAR(180) NOT NULL,
 description VARCHAR(1000) NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_flashcard_decks_owner ON flashcard_decks(owner_id, updated_at DESC);
CREATE TABLE flashcards (
 id UUID PRIMARY KEY, deck_id UUID NOT NULL REFERENCES flashcard_decks(id) ON DELETE CASCADE,
 front VARCHAR(2000) NOT NULL, back VARCHAR(4000) NOT NULL, display_order INTEGER NOT NULL,
 CONSTRAINT uq_flashcard_order UNIQUE(deck_id,display_order),
 CONSTRAINT chk_flashcard_order CHECK (display_order >= 0)
);
