package com.ailearning.platform.commerce.api.usecase;

import com.ailearning.platform.commerce.api.contract.CourseOrderView;
import com.ailearning.platform.commerce.api.contract.CreateCourseOrderResult;
import com.ailearning.platform.commerce.application.command.CreateCourseOrderCommand;

import java.util.List;
import java.util.UUID;

public interface CourseOrderUseCase {
    CreateCourseOrderResult create(CreateCourseOrderCommand command);

    List<CourseOrderView> findRecent(UUID userId, int limit);
}
