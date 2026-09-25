package com.ailearning.platform.catalog.api.contract;

import com.ailearning.platform.catalog.domain.model.ManagedCourse;
import com.ailearning.platform.catalog.domain.model.ManagedLesson;

import java.util.List;

public record CourseWorkspace(ManagedCourse course, List<ManagedLesson> lessons) {
    public CourseWorkspace {
        lessons = List.copyOf(lessons);
    }
}
