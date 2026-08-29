package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.api.contract.EnrollmentView;
import com.ailearning.platform.learning.api.usecase.EnrollmentUseCase;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.model.Enrollment;
import com.ailearning.platform.learning.domain.policy.DirectEnrollmentPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class EnrollmentService implements EnrollmentUseCase {
    private final PublishedCourseLookup courses;
    private final UserLookup users;
    private final EnrollmentStore enrollments;
    private final DirectEnrollmentPolicy policy = new DirectEnrollmentPolicy();
    private final Clock clock;
    public EnrollmentService(PublishedCourseLookup courses, UserLookup users, EnrollmentStore enrollments, Clock clock) {
        this.courses = courses; this.users = users; this.enrollments = enrollments; this.clock = clock;
    }
    @Override
    public EnrollmentView enroll(UUID userId, String slug) {
        PublishedCourseView course = courses.findPublishedBySlug(slug).orElseThrow(this::courseNotFound);
        if (!users.exists(userId)) throw new BusinessException("user_not_found", ErrorType.NOT_FOUND, "Không tìm thấy người dùng.");
        policy.ensureAllowed(course.price());
        enrollments.insertActiveIfAbsent(UUID.randomUUID(), userId, course.id(), clock.instant());
        Enrollment enrollment = enrollments.find(userId, course.id()).orElseThrow(() -> new IllegalStateException("Enrollment insert did not produce a readable row"));
        return view(enrollment, course);
    }
    @Override
    public List<EnrollmentView> findMine(UUID userId) {
        return enrollments.findByUser(userId).stream().map(enrollment -> view(enrollment,
                courses.findPublishedById(enrollment.courseId()).orElseThrow(this::courseNotFound))).toList();
    }
    private EnrollmentView view(Enrollment enrollment, PublishedCourseView course) {
        return new EnrollmentView(enrollment.id(), enrollment.status(), enrollment.enrolledAt(), enrollment.completedAt(), course);
    }
    private BusinessException courseNotFound() { return new BusinessException("course_not_found", ErrorType.NOT_FOUND, "Không tìm thấy khóa học đã xuất bản."); }
}
