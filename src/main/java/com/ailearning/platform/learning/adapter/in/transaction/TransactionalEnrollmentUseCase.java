package com.ailearning.platform.learning.adapter.in.transaction;

import com.ailearning.platform.learning.api.contract.EnrollmentView;
import com.ailearning.platform.learning.api.usecase.EnrollmentUseCase;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

public class TransactionalEnrollmentUseCase implements EnrollmentUseCase {
    private final EnrollmentUseCase delegate;
    private final TransactionTemplate transactions;
    public TransactionalEnrollmentUseCase(EnrollmentUseCase delegate, TransactionTemplate transactions) {
        this.delegate = delegate; this.transactions = transactions;
    }
    @Override public EnrollmentView enroll(UUID userId, String slug) {
        return transactions.execute(status -> delegate.enroll(userId, slug));
    }
    @Override public List<EnrollmentView> findMine(UUID userId) {
        return transactions.execute(status -> delegate.findMine(userId));
    }
}
