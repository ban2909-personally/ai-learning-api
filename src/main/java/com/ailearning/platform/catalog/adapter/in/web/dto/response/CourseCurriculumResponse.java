package com.ailearning.platform.catalog.adapter.in.web.dto.response;

import com.ailearning.platform.catalog.api.contract.CourseCurriculumView;
import java.util.List;
import java.util.UUID;

public record CourseCurriculumResponse(UUID courseId, String courseSlug, String courseTitle, List<SectionResponse> sections) {
    public static CourseCurriculumResponse from(CourseCurriculumView view) {
        return new CourseCurriculumResponse(view.courseId(), view.courseSlug(), view.courseTitle(), view.sections().stream().map(SectionResponse::from).toList());
    }
    public record SectionResponse(UUID id, String title, int order, List<LessonResponse> lessons) {
        static SectionResponse from(CourseCurriculumView.Section value) {
            return new SectionResponse(value.id(), value.title(), value.order(), value.lessons().stream().map(LessonResponse::from).toList());
        }
    }
    public record LessonResponse(UUID id, String title, int durationSeconds, boolean preview, String contentUrl, int order) {
        static LessonResponse from(CourseCurriculumView.Lesson value) {
            return new LessonResponse(value.id(), value.title(), value.durationSeconds(), value.preview(), value.contentUrl(), value.order());
        }
    }
}
