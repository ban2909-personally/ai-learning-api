package com.ailearning.platform.commerce.application.service.impl;

import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.commerce.api.contract.CourseOrderView;
import com.ailearning.platform.commerce.api.contract.CreateCourseOrderResult;
import com.ailearning.platform.commerce.api.usecase.CourseOrderUseCase;
import com.ailearning.platform.commerce.application.command.CreateCourseOrderCommand;
import com.ailearning.platform.commerce.application.port.out.CourseOrderStore;
import com.ailearning.platform.commerce.domain.model.CourseOrder;
import com.ailearning.platform.commerce.domain.policy.PaidCourseOrderPolicy;
import com.ailearning.platform.commerce.domain.valueobject.Money;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.api.usecase.access.EnrollmentAccessLookup;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CourseOrderService implements CourseOrderUseCase {
    private static final int MAX_HISTORY_LIMIT = 100;

    private final PublishedCourseLookup courses;
    private final UserLookup users;
    private final EnrollmentAccessLookup enrollmentAccess;
    private final CourseOrderStore orders;
    private final PaidCourseOrderPolicy policy;
    private final Clock clock;
    private final Duration pendingTtl;

    public CourseOrderService(
            PublishedCourseLookup courses,
            UserLookup users,
            EnrollmentAccessLookup enrollmentAccess,
            CourseOrderStore orders,
            PaidCourseOrderPolicy policy,
            Clock clock,
            Duration pendingTtl
    ) {
        this.courses = Objects.requireNonNull(courses, "courses is required");
        this.users = Objects.requireNonNull(users, "users is required");
        this.enrollmentAccess = Objects.requireNonNull(enrollmentAccess, "enrollmentAccess is required");
        this.orders = Objects.requireNonNull(orders, "orders is required");
        this.policy = Objects.requireNonNull(policy, "policy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.pendingTtl = Objects.requireNonNull(pendingTtl, "pendingTtl is required");
        if (pendingTtl.isZero() || pendingTtl.isNegative()) {
            throw new IllegalArgumentException("pendingTtl must be positive");
        }
    }

    @Override
    public CreateCourseOrderResult create(CreateCourseOrderCommand command) {
        Objects.requireNonNull(command, "command is required");
        Instant now = clock.instant();
        var existing = orders.findByIdempotencyKey(command.userId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return new CreateCourseOrderResult(toView(existing.orElseThrow(), now), false);
        }

        PublishedCourseView course = courses.findPublishedBySlug(command.courseSlug())
                .orElseThrow(() -> new BusinessException(
                        "course_not_found",
                        ErrorType.NOT_FOUND,
                        "Không tìm thấy khóa học đã xuất bản."
                ));
        if (!users.exists(command.userId())) {
            throw new BusinessException("user_not_found", ErrorType.NOT_FOUND, "Không tìm thấy người dùng.");
        }
        policy.ensurePaid(course.price());
        policy.ensureNotEnrolled(enrollmentAccess.hasLearningAccess(command.userId(), course.id()));

        CourseOrder candidate = new CourseOrder(
                UUID.randomUUID(),
                command.userId(),
                course.id(),
                course.slug(),
                course.title(),
                new Money(course.price(), course.currency()),
                command.idempotencyKey(),
                now,
                now.plus(pendingTtl)
        );
        CourseOrder stored = orders.insertOrGet(candidate);
        return new CreateCourseOrderResult(toView(stored, now), stored.id().equals(candidate.id()));
    }

    @Override
    public List<CourseOrderView> findRecent(UUID userId, int limit) {
        Objects.requireNonNull(userId, "userId is required");
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new BusinessException(
                    "invalid_order_limit",
                    ErrorType.BAD_REQUEST,
                    "Giới hạn lịch sử đơn hàng phải từ 1 đến 100."
            );
        }
        Instant now = clock.instant();
        return orders.findRecentByUser(userId, limit).stream()
                .map(order -> toView(order, now))
                .toList();
    }

    private CourseOrderView toView(CourseOrder order, Instant now) {
        return new CourseOrderView(
                order.id(),
                order.courseId(),
                order.courseSlug(),
                order.courseTitle(),
                order.total().amount(),
                order.total().currency(),
                order.statusAt(now).name(),
                order.createdAt(),
                order.expiresAt()
        );
    }
}
