package com.ailearning.platform.organization.domain.enums;

public enum OrganizationRole {
    OWNER(true),
    ADMIN(true),
    MEMBER(false);

    private final boolean canViewMemberships;

    OrganizationRole(boolean canViewMemberships) {
        this.canViewMemberships = canViewMemberships;
    }

    public boolean canViewMemberships() {
        return canViewMemberships;
    }
}
