package com.ailearning.platform.organization.application.port.out;

import com.ailearning.platform.organization.domain.model.Organization;
import com.ailearning.platform.organization.domain.model.OrganizationMembership;

import java.util.Objects;

public record OrganizationMembershipDetails(
        Organization organization,
        OrganizationMembership membership
) {
    public OrganizationMembershipDetails {
        Objects.requireNonNull(organization, "organization is required");
        Objects.requireNonNull(membership, "membership is required");
        if (!organization.id().equals(membership.organizationId())) {
            throw new IllegalArgumentException("membership must belong to the organization");
        }
    }
}
