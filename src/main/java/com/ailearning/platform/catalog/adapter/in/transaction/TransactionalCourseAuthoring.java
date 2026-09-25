package com.ailearning.platform.catalog.adapter.in.transaction;

import com.ailearning.platform.catalog.api.contract.*;
import com.ailearning.platform.catalog.api.usecase.CourseAuthoringUseCase;
import com.ailearning.platform.catalog.application.port.out.PopularCatalogCache;
import com.ailearning.platform.catalog.domain.model.ManagedCourse;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

public class TransactionalCourseAuthoring implements CourseAuthoringUseCase {
    private final CourseAuthoringUseCase delegate;
    private final TransactionTemplate transactions;
    private final PopularCatalogCache cache;

    public TransactionalCourseAuthoring(
            CourseAuthoringUseCase delegate,
            TransactionTemplate transactions,
            PopularCatalogCache cache) {
        this.delegate = delegate;
        this.transactions = transactions;
        this.cache = cache;
    }

    public List<ManagedCourse> list(UUID actor, int page) {
        return transactions.execute(tx -> delegate.list(actor, page));
    }

    public java.util.Map<String, Long> statistics(UUID actor) {
        return transactions.execute(tx -> delegate.statistics(actor));
    }

    public CourseWorkspace get(UUID actor, UUID id) {
        return transactions.execute(tx -> delegate.get(actor, id));
    }

    public ManagedCourse create(UUID actor, CreateCourseCommand command) {
        return transactions.execute(tx -> delegate.create(actor, command));
    }

    public void addLesson(UUID actor, UUID id, CreateLessonCommand command) {
        transactions.executeWithoutResult(tx -> delegate.addLesson(actor, id, command));
    }

    public void transition(UUID actor, UUID id, String status) {
        transactions.executeWithoutResult(
                tx -> {
                    delegate.transition(actor, id, status);
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .registerSynchronization(
                                    new org.springframework.transaction.support
                                            .TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                            cache.evictPublishedPages();
                                        }
                                    });
                });
    }
}
