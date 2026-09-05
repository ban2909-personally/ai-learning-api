package com.ailearning.platform.commerce.adapter.in.transaction;

import com.ailearning.platform.commerce.api.contract.CourseOrderView;
import com.ailearning.platform.commerce.api.contract.CreateCourseOrderResult;
import com.ailearning.platform.commerce.api.usecase.CourseOrderUseCase;
import com.ailearning.platform.commerce.application.command.CreateCourseOrderCommand;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

public class TransactionalCourseOrderUseCase implements CourseOrderUseCase {
    private final CourseOrderUseCase delegate;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public TransactionalCourseOrderUseCase(
            CourseOrderUseCase delegate,
            TransactionTemplate writeTransaction,
            TransactionTemplate readTransaction
    ) {
        this.delegate = delegate;
        this.writeTransaction = writeTransaction;
        this.readTransaction = readTransaction;
    }

    @Override
    public CreateCourseOrderResult create(CreateCourseOrderCommand command) {
        return writeTransaction.execute(status -> delegate.create(command));
    }

    @Override
    public List<CourseOrderView> findRecent(UUID userId, int limit) {
        return readTransaction.execute(status -> delegate.findRecent(userId, limit));
    }
}
