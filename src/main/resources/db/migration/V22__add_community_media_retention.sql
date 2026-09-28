ALTER TABLE community_media_assets ADD COLUMN expires_at TIMESTAMPTZ;
UPDATE community_media_assets SET expires_at=created_at+INTERVAL '14 days';
ALTER TABLE community_media_assets ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE community_media_assets ALTER COLUMN expires_at SET DEFAULT CURRENT_TIMESTAMP+INTERVAL '14 days';
ALTER TABLE community_media_assets ADD COLUMN cleanup_claimed_until TIMESTAMPTZ;
ALTER TABLE community_media_assets ADD COLUMN storage_deleted_at TIMESTAMPTZ;
CREATE INDEX idx_community_media_cleanup ON community_media_assets(expires_at,id)
    WHERE storage_deleted_at IS NULL;
