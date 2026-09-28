ALTER TABLE community_post_likes ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'LIKE';
ALTER TABLE community_post_likes ADD CONSTRAINT chk_community_reaction_kind
    CHECK (kind IN ('LIKE','LOVE','CARE','HAHA','WOW','SAD','ANGRY'));
