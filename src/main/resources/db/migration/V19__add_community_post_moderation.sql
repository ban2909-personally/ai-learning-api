-- Existing published content and membership records are preserved.
ALTER TABLE community_spaces DROP CONSTRAINT chk_public_page;
ALTER TABLE community_posts DROP CONSTRAINT community_posts_status_check;
ALTER TABLE community_posts ADD CONSTRAINT chk_community_post_status
    CHECK (status IN ('ACTIVE', 'PENDING', 'REJECTED', 'REMOVED'));
CREATE INDEX idx_community_posts_pending ON community_posts(space_id, created_at, id)
    WHERE status = 'PENDING';
CREATE INDEX idx_community_comment_preview ON community_comments(post_id, created_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND parent_id IS NULL;
