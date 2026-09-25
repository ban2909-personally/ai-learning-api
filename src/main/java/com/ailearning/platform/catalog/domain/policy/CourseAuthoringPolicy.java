package com.ailearning.platform.catalog.domain.policy;

import com.ailearning.platform.catalog.domain.model.ManagedCourse;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Set;
import java.util.UUID;

public class CourseAuthoringPolicy {
    public boolean reviewer(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("LEADER");
    }

    public boolean author(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("LECTURE") || roles.contains("INSTRUCTOR");
    }

    public void ensureOwner(UUID actor, Set<String> roles, ManagedCourse course) {
        if (!author(roles) || (!roles.contains("ADMIN") && !actor.equals(course.instructorId()))) {
            throw new BusinessException(
                    "course_management_denied",
                    ErrorType.FORBIDDEN,
                    "Bạn không có quyền chỉnh sửa khóa học này.");
        }
    }

    public void ensureTransition(
            UUID actor, Set<String> roles, ManagedCourse course, String target) {
        boolean review = reviewer(roles);
        boolean allowed =
                switch (target) {
                    case "PENDING_REVIEW" ->
                            course.status().equals("DRAFT")
                                    && author(roles)
                                    && (roles.contains("ADMIN")
                                            || actor.equals(course.instructorId()));
                    case "PUBLISHED" -> review && course.status().equals("PENDING_REVIEW");
                    case "DRAFT" -> review && course.status().equals("PENDING_REVIEW");
                    case "ARCHIVED" -> review && course.status().equals("PUBLISHED");
                    default -> false;
                };
        if (!allowed)
            throw new BusinessException(
                    "course_transition_denied",
                    ErrorType.FORBIDDEN,
                    "Vai trò hoặc trạng thái hiện tại không cho phép thao tác này.");
        if (Set.of("PENDING_REVIEW", "PUBLISHED").contains(target) && course.lessonCount() == 0) {
            throw new BusinessException(
                    "curriculum_required",
                    ErrorType.BAD_REQUEST,
                    "Cần ít nhất một bài học trước khi gửi duyệt hoặc xuất bản.");
        }
    }
}
