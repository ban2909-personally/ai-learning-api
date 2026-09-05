package com.ailearning.platform.commerce.application.service.impl;

import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.commerce.application.command.CreateCourseOrderCommand;
import com.ailearning.platform.commerce.application.port.out.CourseOrderStore;
import com.ailearning.platform.commerce.domain.model.CourseOrder;
import com.ailearning.platform.commerce.domain.policy.PaidCourseOrderPolicy;
import com.ailearning.platform.commerce.domain.valueobject.Money;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.api.usecase.access.EnrollmentAccessLookup;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourseOrderServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private final PublishedCourseLookup courses = mock(PublishedCourseLookup.class);
    private final UserLookup users = mock(UserLookup.class);
    private final EnrollmentAccessLookup enrollmentAccess = mock(EnrollmentAccessLookup.class);
    private final CourseOrderStore orders = mock(CourseOrderStore.class);
    private final CourseOrderService service = new CourseOrderService(
            courses,
            users,
            enrollmentAccess,
            orders,
            new PaidCourseOrderPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            PENDING_TTL
    );

    private UUID userId;
    private UUID courseId;
    private UUID idempotencyKey;
    private PublishedCourseView course;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();
        course = course(new BigDecimal("250000.00"));
    }

    @Test
    void createsAPendingOrderFromTrustedCourseAndAccessBoundaries() {
        when(orders.findByIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(courses.findPublishedBySlug("spring-boot")).thenReturn(Optional.of(course));
        when(users.exists(userId)).thenReturn(true);
        when(enrollmentAccess.hasLearningAccess(userId, courseId)).thenReturn(false);
        when(orders.insertOrGet(any(CourseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new CreateCourseOrderCommand(userId, "spring-boot", idempotencyKey));

        assertThat(result.created()).isTrue();
        assertThat(result.order().courseId()).isEqualTo(courseId);
        assertThat(result.order().amount()).isEqualByComparingTo("250000.00");
        assertThat(result.order().currency()).isEqualTo("VND");
        assertThat(result.order().status()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.order().createdAt()).isEqualTo(NOW);
        assertThat(result.order().expiresAt()).isEqualTo(NOW.plus(PENDING_TTL));
    }

    @Test
    void replaysTheOriginalSnapshotWithoutReadingMutableModules() {
        CourseOrder existing = order(UUID.randomUUID(), idempotencyKey, NOW.minusSeconds(60));
        when(orders.findByIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.of(existing));

        var result = service.create(new CreateCourseOrderCommand(userId, "changed-slug", idempotencyKey));

        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(existing.id());
        assertThat(result.order().courseSlug()).isEqualTo("spring-boot");
        verifyNoInteractions(courses, users, enrollmentAccess);
        verify(orders, never()).insertOrGet(any());
    }

    @Test
    void reportsAConcurrentIdempotentInsertAsAReplay() {
        CourseOrder winner = order(UUID.randomUUID(), idempotencyKey, NOW);
        when(orders.findByIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(courses.findPublishedBySlug("spring-boot")).thenReturn(Optional.of(course));
        when(users.exists(userId)).thenReturn(true);
        when(enrollmentAccess.hasLearningAccess(userId, courseId)).thenReturn(false);
        when(orders.insertOrGet(any(CourseOrder.class))).thenReturn(winner);

        var result = service.create(new CreateCourseOrderCommand(userId, "spring-boot", idempotencyKey));

        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(winner.id());
    }

    @Test
    void rejectsFreeOrAlreadyEnrolledCoursesBeforePersistence() {
        when(orders.findByIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
        when(courses.findPublishedBySlug("spring-boot")).thenReturn(Optional.of(course(BigDecimal.ZERO)));
        when(users.exists(userId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("course_is_free"));
        verifyNoInteractions(enrollmentAccess);
        verify(orders, never()).insertOrGet(any());

        when(courses.findPublishedBySlug("spring-boot")).thenReturn(Optional.of(course));
        when(enrollmentAccess.hasLearningAccess(userId, courseId)).thenReturn(true);
        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("course_already_enrolled"));
        verify(orders, never()).insertOrGet(any());
    }

    @Test
    void returnsBoundedHistoryWithEffectiveExpiry() {
        CourseOrder pending = order(UUID.randomUUID(), UUID.randomUUID(), NOW);
        CourseOrder expired = order(UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(3600));
        when(orders.findRecentByUser(userId, 20)).thenReturn(List.of(pending, expired));

        var history = service.findRecent(userId, 20);

        assertThat(history).extracting(view -> view.status())
                .containsExactly("PENDING_PAYMENT", "EXPIRED");
        assertThatThrownBy(() -> service.findRecent(userId, 101))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("invalid_order_limit"));
    }

    private CreateCourseOrderCommand command() {
        return new CreateCourseOrderCommand(userId, "spring-boot", idempotencyKey);
    }

    private PublishedCourseView course(BigDecimal price) {
        return new PublishedCourseView(
                courseId,
                "spring-boot",
                "Spring Boot Production",
                "Xây dựng dịch vụ production.",
                "INTERMEDIATE",
                price,
                "VND",
                null,
                180,
                new PublishedCourseView.CategoryView(UUID.randomUUID(), "backend", "Backend", null),
                "Giảng viên"
        );
    }

    private CourseOrder order(UUID id, UUID key, Instant createdAt) {
        return new CourseOrder(
                id,
                userId,
                courseId,
                "spring-boot",
                "Spring Boot Production",
                new Money(new BigDecimal("250000.00"), "VND"),
                key,
                createdAt,
                createdAt.plus(PENDING_TTL)
        );
    }
}
