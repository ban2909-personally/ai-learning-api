package com.ailearning.platform.learning.api.usecase;

import com.ailearning.platform.learning.api.contract.EnrollmentView;
import java.util.List;
import java.util.UUID;

public interface EnrollmentUseCase {
    EnrollmentView enroll(UUID userId, String courseSlug);
    List<EnrollmentView> findMine(UUID userId);
}
