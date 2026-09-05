package com.ailearning.platform.organization.api.usecase;

import com.ailearning.platform.organization.api.contract.CreateOrganizationResult;
import com.ailearning.platform.organization.api.contract.OrganizationMemberView;
import com.ailearning.platform.organization.api.contract.OrganizationView;
import com.ailearning.platform.organization.application.command.CreateOrganizationCommand;

import java.util.List;
import java.util.UUID;

public interface OrganizationUseCase {
    CreateOrganizationResult create(CreateOrganizationCommand command);

    List<OrganizationView> findMine(UUID userId, int limit);

    List<OrganizationMemberView> findMembers(UUID requesterId, UUID organizationId, int limit);
}
