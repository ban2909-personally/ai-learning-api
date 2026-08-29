package com.ailearning.platform.learning.adapter.in.transaction;

import com.ailearning.platform.learning.api.contract.LessonProgressView;
import com.ailearning.platform.learning.api.usecase.LessonProgressUseCase;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

public class TransactionalLessonProgressUseCase implements LessonProgressUseCase {
    private final LessonProgressUseCase delegate;
    private final TransactionTemplate transactions;
    public TransactionalLessonProgressUseCase(LessonProgressUseCase delegate, TransactionTemplate transactions) {
        this.delegate = delegate; this.transactions = transactions;
    }
    @Override public LessonProgressView find(UUID userId, String courseSlug, UUID lessonId) {
        return transactions.execute(status -> delegate.find(userId, courseSlug, lessonId));
    }
    @Override public LessonProgressView save(UUID userId, String courseSlug, UUID lessonId,
            int positionSeconds, boolean completed) {
        return transactions.execute(status -> delegate.save(userId, courseSlug, lessonId, positionSeconds, completed));
    }
}
