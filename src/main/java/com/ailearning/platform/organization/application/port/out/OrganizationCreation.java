package com.ailearning.platform.organization.application.port.out;

import java.util.Objects;

public record OrganizationCreation(OrganizationMembershipDetails details, boolean created) {
    public OrganizationCreation {
        Objects.requireNonNull(details, "details is required");
    }
}
