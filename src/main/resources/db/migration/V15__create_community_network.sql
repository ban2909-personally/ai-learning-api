CREATE TABLE community_spaces (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    kind VARCHAR(8) NOT NULL CHECK (kind IN ('GROUP', 'PAGE')),
    visibility VARCHAR(8) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_public_page CHECK (kind <> 'PAGE' OR visibility = 'PUBLIC')
);
CREATE INDEX idx_community_spaces_search ON community_spaces (lower(name));
CREATE INDEX idx_community_spaces_created ON community_spaces (created_at DESC, id DESC);

CREATE TABLE community_members (
    space_id UUID NOT NULL REFERENCES community_spaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(8) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    status VARCHAR(8) NOT NULL CHECK (status IN ('ACTIVE', 'PENDING', 'INVITED')),
    invited_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    joined_at TIMESTAMPTZ,
    PRIMARY KEY (space_id, user_id),
    CONSTRAINT chk_owner_active CHECK (role <> 'OWNER' OR status = 'ACTIVE')
);
CREATE INDEX idx_community_members_user ON community_members (user_id, status, space_id);
CREATE INDEX idx_community_members_space ON community_members (space_id, status, role);

CREATE TABLE community_posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users(id),
    space_id UUID REFERENCES community_spaces(id),
    shared_post_id UUID REFERENCES community_posts(id),
    body VARCHAR(5000) NOT NULL DEFAULT '',
    status VARCHAR(8) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_post_has_content CHECK (length(trim(body)) > 0 OR shared_post_id IS NOT NULL)
);
CREATE INDEX idx_community_posts_feed ON community_posts (created_at DESC, id DESC)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_community_posts_space ON community_posts (space_id, created_at DESC, id DESC)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_community_posts_author ON community_posts (author_id, created_at DESC);

CREATE TABLE community_post_likes (
    post_id UUID NOT NULL REFERENCES community_posts(id),
    user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_community_post_likes_user ON community_post_likes (user_id, created_at DESC);

CREATE TABLE community_comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES community_posts(id),
    parent_id UUID REFERENCES community_comments(id),
    author_id UUID NOT NULL REFERENCES users(id),
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(8) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_comment_body CHECK (length(trim(body)) > 0)
);
CREATE INDEX idx_community_comments_post ON community_comments (post_id, created_at, id);
