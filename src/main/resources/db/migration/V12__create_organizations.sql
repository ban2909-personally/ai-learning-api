CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    created_by UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organizations_creator_idempotency UNIQUE (created_by, idempotency_key),
    CONSTRAINT ck_organizations_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organization_memberships_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_organization_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_organization_memberships_user_recent
    ON organization_memberships (user_id, joined_at DESC, id DESC);

CREATE INDEX idx_organization_memberships_org_roster
    ON organization_memberships (organization_id, joined_at ASC, id ASC);
