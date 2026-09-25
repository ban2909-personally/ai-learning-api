package com.ailearning.platform.catalog.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.catalog.domain.model.ManagedCourse;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

class CourseAuthoringPolicyTest {
    private final CourseAuthoringPolicy policy = new CourseAuthoringPolicy();
    private final UUID owner = UUID.randomUUID();

    private ManagedCourse course(String status, int lessons) {
        return new ManagedCourse(
                UUID.randomUUID(),
                owner,
                UUID.randomUUID(),
                "demo",
                "Demo",
                "Intro",
                "Description",
                "BEGINNER",
                BigDecimal.ZERO,
                status,
                lessons,
                Instant.EPOCH);
    }

    @Test
    void onlyOwnerOrAdministratorCanEdit() {
        assertDoesNotThrow(() -> policy.ensureOwner(owner, Set.of("LECTURE"), course("DRAFT", 1)));
        assertThrows(
                BusinessException.class,
                () -> policy.ensureOwner(UUID.randomUUID(), Set.of("LECTURE"), course("DRAFT", 1)));
        assertThrows(
                BusinessException.class,
                () -> policy.ensureOwner(owner, Set.of("STUDENT"), course("DRAFT", 1)));
        assertDoesNotThrow(
                () -> policy.ensureOwner(UUID.randomUUID(), Set.of("ADMIN"), course("DRAFT", 1)));
    }

    @Test
    void authorSubmitsButCannotSelfPublish() {
        assertDoesNotThrow(
                () ->
                        policy.ensureTransition(
                                owner, Set.of("LECTURE"), course("DRAFT", 1), "PENDING_REVIEW"));
        assertThrows(
                BusinessException.class,
                () ->
                        policy.ensureTransition(
                                owner,
                                Set.of("LECTURE"),
                                course("PENDING_REVIEW", 1),
                                "PUBLISHED"));
        assertDoesNotThrow(
                () ->
                        policy.ensureTransition(
                                UUID.randomUUID(),
                                Set.of("LEADER"),
                                course("PENDING_REVIEW", 1),
                                "PUBLISHED"));
    }

    @Test
    void rejectsEmptyCurriculumAndInvalidTransitions() {
        assertThrows(
                BusinessException.class,
                () ->
                        policy.ensureTransition(
                                owner, Set.of("LECTURE"), course("DRAFT", 0), "PENDING_REVIEW"));
        assertThrows(
                BusinessException.class,
                () ->
                        policy.ensureTransition(
                                owner, Set.of("ADMIN"), course("DRAFT", 1), "PUBLISHED"));
    }
}
