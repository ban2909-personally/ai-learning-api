package com.ailearning.platform.identity.adapter.in.transaction;

import com.ailearning.platform.identity.api.contract.AccountPage;
import com.ailearning.platform.identity.api.usecase.AccountManagementUseCase;
import com.ailearning.platform.identity.domain.model.ManagedAccount;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

public class TransactionalAccountManagement implements AccountManagementUseCase {
    private final AccountManagementUseCase delegate;
    private final TransactionTemplate transactions;

    public TransactionalAccountManagement(
            AccountManagementUseCase delegate, TransactionTemplate transactions) {
        this.delegate = delegate;
        this.transactions = transactions;
    }

    public AccountPage list(UUID actor, String search, String role, int page, int size) {
        return transactions.execute(tx -> delegate.list(actor, search, role, page, size));
    }

    public java.util.Map<String, Long> statistics(UUID actor) {
        return transactions.execute(tx -> delegate.statistics(actor));
    }

    public ManagedAccount create(
            UUID actor, String email, String name, String password, String role) {
        return transactions.execute(tx -> delegate.create(actor, email, name, password, role));
    }

    public void update(UUID actor, UUID account, String role, String status) {
        transactions.executeWithoutResult(tx -> delegate.update(actor, account, role, status));
    }
}
