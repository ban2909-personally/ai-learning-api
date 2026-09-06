package com.ailearning.platform.organization.adapter.in.transaction;

import com.ailearning.platform.organization.api.contract.CreateOrganizationResult;
import com.ailearning.platform.organization.api.contract.OrganizationMemberView;
import com.ailearning.platform.organization.api.contract.OrganizationView;
import com.ailearning.platform.organization.api.usecase.OrganizationUseCase;
import com.ailearning.platform.organization.application.command.CreateOrganizationCommand;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

public class TransactionalOrganizationUseCase implements OrganizationUseCase {
    private final OrganizationUseCase delegate;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public TransactionalOrganizationUseCase(
            OrganizationUseCase delegate,
            TransactionTemplate writeTransaction,
            TransactionTemplate readTransaction
    ) {
        this.delegate = delegate;
        this.writeTransaction = writeTransaction;
        this.readTransaction = readTransaction;
    }

    @Override
    public CreateOrganizationResult create(CreateOrganizationCommand command) {
        return writeTransaction.execute(status -> delegate.create(command));
    }

    @Override
    public List<OrganizationView> findMine(UUID userId, int limit) {
        return readTransaction.execute(status -> delegate.findMine(userId, limit));
    }

    @Override
    public List<OrganizationMemberView> findMembers(UUID requesterId, UUID organizationId, int limit) {
        return readTransaction.execute(status -> delegate.findMembers(requesterId, organizationId, limit));
    }
}
