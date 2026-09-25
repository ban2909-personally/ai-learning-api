package com.ailearning.platform.catalog.api.usecase;

import com.ailearning.platform.catalog.api.contract.*;
import com.ailearning.platform.catalog.domain.model.ManagedCourse;

import java.util.List;
import java.util.UUID;

public interface CourseAuthoringUseCase {
    java.util.Map<String, Long> statistics(UUID actor);

    List<ManagedCourse> list(UUID actor, int page);

    CourseWorkspace get(UUID actor, UUID course);

    ManagedCourse create(UUID actor, CreateCourseCommand command);

    void addLesson(UUID actor, UUID course, CreateLessonCommand command);

    void transition(UUID actor, UUID course, String status);
}
