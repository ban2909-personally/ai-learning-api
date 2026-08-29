package com.ailearning.platform.identity.adapter.in.transaction;

import com.ailearning.platform.identity.api.contract.*;
import com.ailearning.platform.identity.api.usecase.IdentityUseCase;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

public class TransactionalIdentityUseCase implements IdentityUseCase, UserLookup {
    private final IdentityUseCase identity;
    private final UserLookup users;
    private final TransactionTemplate transactions;
    public TransactionalIdentityUseCase(IdentityUseCase identity, UserLookup users, TransactionTemplate transactions) {
        this.identity = identity; this.users = users; this.transactions = transactions;
    }
    @Override public AuthSession register(RegisterCommand command) { return transactions.execute(status -> identity.register(command)); }
    @Override public AuthSession login(LoginCommand command) { return transactions.execute(status -> identity.login(command)); }
    @Override public AuthSession refresh(String token) { return transactions.execute(status -> identity.refresh(token)); }
    @Override public void logout(String token) { transactions.executeWithoutResult(status -> identity.logout(token)); }
    @Override public UserView me(UUID id) { return transactions.execute(status -> identity.me(id)); }
    @Override public boolean exists(UUID id) { return Boolean.TRUE.equals(transactions.execute(status -> users.exists(id))); }
}
