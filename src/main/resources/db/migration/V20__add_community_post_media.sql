CREATE TABLE community_media_assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    object_key VARCHAR(200) NOT NULL UNIQUE,
    content_type VARCHAR(40) NOT NULL CHECK (content_type IN ('image/jpeg','image/png','image/webp','video/mp4','video/webm')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 9999999),
    etag VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE community_posts ADD COLUMN media_id UUID UNIQUE REFERENCES community_media_assets(id);
ALTER TABLE community_posts DROP CONSTRAINT chk_post_has_content;
ALTER TABLE community_posts ADD CONSTRAINT chk_post_has_content
    CHECK (length(trim(body)) > 0 OR shared_post_id IS NOT NULL OR media_id IS NOT NULL);
