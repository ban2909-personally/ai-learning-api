package com.ailearning.platform.learning.api.usecase.access;

import java.util.UUID;

public interface EnrollmentAccessLookup {
    boolean hasLearningAccess(UUID userId, UUID courseId);
}
