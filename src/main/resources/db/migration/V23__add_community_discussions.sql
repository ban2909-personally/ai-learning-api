CREATE TABLE community_post_features (
 post_id UUID PRIMARY KEY REFERENCES community_posts(id) ON DELETE CASCADE,
 attachment_url VARCHAR(2048), background_color VARCHAR(7), font_color VARCHAR(7),
 CHECK ((background_color IS NULL AND font_color IS NULL) OR
 (background_color ~ '^#[0-9A-Fa-f]{6}$' AND font_color ~ '^#[0-9A-Fa-f]{6}$'))
);
CREATE TABLE community_polls (
 post_id UUID PRIMARY KEY REFERENCES community_posts(id) ON DELETE CASCADE,
 kind VARCHAR(12) NOT NULL CHECK(kind IN ('POLL','ELECTION')),
 question VARCHAR(300) NOT NULL CHECK(length(trim(question)) BETWEEN 1 AND 300),
 closes_at TIMESTAMPTZ, closed_at TIMESTAMPTZ
);
CREATE TABLE community_poll_options (
 id UUID PRIMARY KEY, post_id UUID NOT NULL REFERENCES community_polls(post_id) ON DELETE CASCADE,
 label VARCHAR(160) NOT NULL CHECK(length(trim(label)) BETWEEN 1 AND 160),
 position SMALLINT NOT NULL CHECK(position BETWEEN 0 AND 7),
 UNIQUE(post_id,position), UNIQUE(post_id,label), UNIQUE(post_id,id)
);
CREATE TABLE community_poll_votes (
 post_id UUID NOT NULL, user_id UUID NOT NULL REFERENCES users(id), option_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(post_id,user_id),
 FOREIGN KEY(post_id,option_id) REFERENCES community_poll_options(post_id,id) ON DELETE CASCADE
);
CREATE INDEX idx_community_poll_votes_option ON community_poll_votes(option_id);
