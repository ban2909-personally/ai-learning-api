CREATE TABLE identity_social_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    bio varchar(400) NOT NULL DEFAULT '',
    location varchar(120) NOT NULL DEFAULT '',
    website varchar(500) NOT NULL DEFAULT '',
    cover_theme varchar(20) NOT NULL DEFAULT 'aurora'
        CHECK (cover_theme IN ('aurora', 'ocean', 'sunset', 'forest')),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE community_friendships (
    user_left uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_right uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    initiator_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status varchar(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_left, user_right),
    CHECK (user_left < user_right),
    CHECK (initiator_id IN (user_left, user_right))
);
CREATE INDEX community_friendships_right_idx ON community_friendships(user_right, status);
CREATE INDEX community_friendships_left_status_idx ON community_friendships(user_left, status);
