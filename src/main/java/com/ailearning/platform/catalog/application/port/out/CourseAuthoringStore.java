package com.ailearning.platform.catalog.application.port.out;

import com.ailearning.platform.catalog.api.contract.CreateCourseCommand;
import com.ailearning.platform.catalog.api.contract.CreateLessonCommand;
import com.ailearning.platform.catalog.domain.model.ManagedCourse;
import com.ailearning.platform.catalog.domain.model.ManagedLesson;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseAuthoringStore {
    java.util.Map<String, Long> statistics(UUID owner);

    List<ManagedCourse> list(UUID owner, int page);

    Optional<ManagedCourse> find(UUID course, boolean lock);

    List<ManagedLesson> lessons(UUID course);

    ManagedCourse create(UUID owner, CreateCourseCommand command);

    void addLesson(UUID course, CreateLessonCommand command);

    void transition(UUID course, String status);
}
