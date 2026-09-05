package com.ailearning.platform.commerce.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.math.BigDecimal;
import java.util.Objects;

public class PaidCourseOrderPolicy {
    public void ensurePaid(BigDecimal price) {
        Objects.requireNonNull(price, "price is required");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price cannot be negative");
        }
        if (price.signum() == 0) {
            throw new BusinessException(
                    "course_is_free",
                    ErrorType.CONFLICT,
                    "Khóa học miễn phí cần được ghi danh trực tiếp."
            );
        }
    }

    public void ensureNotEnrolled(boolean hasLearningAccess) {
        if (hasLearningAccess) {
            throw new BusinessException(
                    "course_already_enrolled",
                    ErrorType.CONFLICT,
                    "Bạn đã có quyền học khóa học này."
            );
        }
    }
}
