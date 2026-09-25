package com.ailearning.platform.catalog.application.service.impl;

import com.ailearning.platform.catalog.api.contract.*;
import com.ailearning.platform.catalog.api.usecase.CourseAuthoringUseCase;
import com.ailearning.platform.catalog.application.port.out.CourseAuthoringStore;
import com.ailearning.platform.catalog.domain.model.ManagedCourse;
import com.ailearning.platform.catalog.domain.policy.CourseAuthoringPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CourseAuthoringService implements CourseAuthoringUseCase {
    private final CourseAuthoringStore store;
    private final AccountAccess accounts;
    private final CourseAuthoringPolicy policy = new CourseAuthoringPolicy();

    public CourseAuthoringService(CourseAuthoringStore store, AccountAccess accounts) {
        this.store = store;
        this.accounts = accounts;
    }

    private Set<String> roles(UUID actor) {
        return accounts.requireActive(actor).roles();
    }

    private ManagedCourse course(UUID id, boolean lock) {
        return store.find(id, lock)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "course_not_found",
                                        ErrorType.NOT_FOUND,
                                        "Không tìm thấy khóa học."));
    }

    @Override
    public List<ManagedCourse> list(UUID actor, int page) {
        var roles = roles(actor);
        if (!policy.author(roles) && !policy.reviewer(roles)) throw denied();
        return store.list(policy.reviewer(roles) ? null : actor, Math.max(0, page));
    }

    @Override
    public java.util.Map<String, Long> statistics(UUID actor) {
        var roles = roles(actor);
        if (!policy.author(roles) && !policy.reviewer(roles)) throw denied();
        return store.statistics(policy.reviewer(roles) ? null : actor);
    }

    @Override
    public CourseWorkspace get(UUID actor, UUID id) {
        var roles = roles(actor);
        var course = course(id, false);
        if (!policy.reviewer(roles)) policy.ensureOwner(actor, roles, course);
        return new CourseWorkspace(course, store.lessons(id));
    }

    @Override
    public ManagedCourse create(UUID actor, CreateCourseCommand command) {
        if (!policy.author(roles(actor))) throw denied();
        return store.create(actor, command);
    }

    @Override
    public void addLesson(UUID actor, UUID id, CreateLessonCommand command) {
        var course = course(id, true);
        policy.ensureOwner(actor, roles(actor), course);
        if (!course.status().equals("DRAFT"))
            throw new BusinessException(
                    "course_not_draft",
                    ErrorType.CONFLICT,
                    "Chỉ có thể thêm bài học khi khóa học ở trạng thái nháp.");
        if (!command.contentUrl().isBlank()) {
            try {
                var uri = URI.create(command.contentUrl());
                if (!"https".equals(uri.getScheme())
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("Invalid lesson URL");
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        "invalid_lesson_url",
                        ErrorType.BAD_REQUEST,
                        "Liên kết bài học phải là HTTPS hợp lệ.");
            }
        }
        store.addLesson(id, command);
    }

    @Override
    public void transition(UUID actor, UUID id, String status) {
        var course = course(id, true);
        policy.ensureTransition(actor, roles(actor), course, status);
        if ("PUBLISHED".equals(status)
                && store.lessons(id).stream().anyMatch(lesson -> lesson.contentUrl().isBlank())) {
            throw new BusinessException(
                    "lesson_content_required",
                    ErrorType.BAD_REQUEST,
                    "Mỗi bài học cần video đã tải lên hoặc liên kết nội dung trước khi xuất bản.");
        }
        store.transition(id, status);
    }

    private BusinessException denied() {
        return new BusinessException(
                "course_authoring_denied",
                ErrorType.FORBIDDEN,
                "Tài khoản không có quyền biên soạn khóa học.");
    }
}
